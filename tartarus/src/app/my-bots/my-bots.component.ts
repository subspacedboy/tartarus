import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { CryptoService } from '../crypto.service';
import { UserDataService } from '../user-data.service';
import { ToastService } from '../toast.service';
import { Bot } from '../models/bot';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmDialogImports } from '../shared/ui/ui-dialog-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-my-bots',
  imports: [
    FormsModule,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    ...HlmDialogImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './my-bots.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class MyBotsComponent implements OnInit {
  bots: Bot[] = [];
  newInternalName = '';
  newDescription = '';
  isCreating = false;

  selectedBotForKey?: Bot;
  derivedPrivatePem = '';
  derivedPublicPem = '';

  constructor(
    private coordinatorService: TartarusCoordinatorService,
    private cryptoService: CryptoService,
    private userDataService: UserDataService,
    private toastService: ToastService,
  ) {}

  ngOnInit() {
    this.loadMyBots();
  }

  loadMyBots() {
    this.coordinatorService.getMyBots().subscribe({
      next: (bots) => {
        this.bots = bots;
      },
      error: () => {
        this.toastService.showError('Failed to load bots for author session');
      },
    });
  }

  async createBot() {
    const internalName = this.newInternalName.trim();
    const description = this.newDescription.trim();
    if (!internalName || !description) {
      this.toastService.showError('Provide both an internal derivation name and a description');
      return;
    }

    const authorKeys = this.userDataService.getAuthorKeypair();
    if (!authorKeys.privatePem) {
      this.toastService.showError('No Author private key found in session');
      return;
    }

    this.isCreating = true;
    try {
      const derived = await this.cryptoService.deriveBotKeyPair(authorKeys.privatePem, internalName, 1);
      this.coordinatorService
        .createBot(derived.compressedPublicKeyBase64, description, internalName, 1)
        .subscribe({
          next: () => {
            this.newInternalName = '';
            this.newDescription = '';
            this.isCreating = false;
            this.toastService.showSuccess(`Registered bot "${internalName}"`);
            this.loadMyBots();
          },
          error: () => {
            this.isCreating = false;
            this.toastService.showError('Failed to register bot');
          },
        });
    } catch (e: any) {
      this.isCreating = false;
      this.toastService.showError(`Key derivation error: ${e?.message || e}`);
    }
  }

  togglePause(bot: Bot) {
    if (!bot.name) return;
    const nextState = bot.state === 'PAUSED' ? 'UNVERIFIED' : 'PAUSED';
    this.coordinatorService.updateBot(bot.name, { state: nextState }).subscribe({
      next: (updated) => {
        bot.state = updated.state;
        this.toastService.showSuccess(`Bot ${bot.name} is now ${updated.state}`);
      },
    });
  }

  async rotateBotKey(bot: Bot) {
    if (!bot.name || !bot.internalName) {
      this.toastService.showError('Bot does not have a deterministic internalName');
      return;
    }
    const authorKeys = this.userDataService.getAuthorKeypair();
    if (!authorKeys.privatePem) return;

    const nextVersion = (bot.keyVersion || 1) + 1;
    try {
      const derived = await this.cryptoService.deriveBotKeyPair(
        authorKeys.privatePem,
        bot.internalName,
        nextVersion,
      );
      this.coordinatorService
        .updateBot(bot.name, {
          publicKey: derived.compressedPublicKeyBase64,
          keyVersion: nextVersion,
        })
        .subscribe({
          next: (updated) => {
            bot.publicKey = updated.publicKey;
            bot.keyVersion = updated.keyVersion;
            this.toastService.showSuccess(`Rotated ${bot.name} to key version v${nextVersion}`);
          },
        });
    } catch (e: any) {
      this.toastService.showError(`Failed to rotate key: ${e?.message || e}`);
    }
  }

  async showDerivedKeyModal(bot: Bot) {
    if (!bot.internalName) return;
    const authorKeys = this.userDataService.getAuthorKeypair();
    if (!authorKeys.privatePem) return;

    const derived = await this.cryptoService.deriveBotKeyPair(
      authorKeys.privatePem,
      bot.internalName,
      bot.keyVersion || 1,
    );
    this.derivedPrivatePem = derived.privateKeyPEM;
    this.derivedPublicPem = derived.publicKeyPEM;
    this.selectedBotForKey = bot;
  }

  closeDerivedKeyModal() {
    this.selectedBotForKey = undefined;
    this.derivedPrivatePem = '';
    this.derivedPublicPem = '';
  }

  async copyText(value: string, label: string) {
    await navigator.clipboard.writeText(value);
    this.toastService.showSuccess(`Copied ${label} to clipboard`);
  }
}
