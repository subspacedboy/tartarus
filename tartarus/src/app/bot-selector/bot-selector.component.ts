import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Bot } from '../models/bot';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmTooltipTriggerDirective } from '../shared/ui/ui-tooltip-helm';

export type BotSortField = 'name' | 'createdAt';
export type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-bot-selector',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmTooltipTriggerDirective,
  ],
  templateUrl: './bot-selector.component.html',
  styleUrl: './bot-selector.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class BotSelectorComponent {
  @Input() bots: Bot[] = [];
  @Input() selectedBotNames: string[] = [];
  @Input() showSelectedTray: boolean = true;

  @Output() botAdded = new EventEmitter<Bot>();
  @Output() botRemoved = new EventEmitter<string>();

  searchQuery: string = '';
  sortField: BotSortField = 'name';
  sortDirection: SortDirection = 'asc';

  copiedBotName: string | null = null;

  get filteredBots(): Bot[] {
    const query = this.searchQuery.trim().toLowerCase();
    let result = this.bots.filter(bot => {
      if (!query) return true;
      const name = (bot.name || '').toLowerCase();
      const desc = (bot.description || '').toLowerCase();
      return name.includes(query) || desc.includes(query);
    });

    result = [...result].sort((a, b) => {
      if (this.sortField === 'name') {
        const nameA = a.name || '';
        const nameB = b.name || '';
        const cmp = nameA.localeCompare(nameB);
        return this.sortDirection === 'asc' ? cmp : -cmp;
      } else {
        const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        const cmp = timeA - timeB;
        return this.sortDirection === 'asc' ? cmp : -cmp;
      }
    });

    return result;
  }

  isBotSelected(botName?: string): boolean {
    if (!botName) return false;
    return this.selectedBotNames.includes(botName);
  }

  toggleSort(field: BotSortField): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = field === 'createdAt' ? 'desc' : 'asc';
    }
  }

  onAdd(bot: Bot): void {
    this.botAdded.emit(bot);
  }

  onRemove(botName?: string): void {
    if (botName) {
      this.botRemoved.emit(botName);
    }
  }

  clearSearch(): void {
    this.searchQuery = '';
  }

  async copyKey(key?: string, botName?: string, event?: Event): Promise<void> {
    if (event) {
      event.stopPropagation();
    }
    if (!key) return;
    try {
      await navigator.clipboard.writeText(key);
      this.copiedBotName = botName || null;
      setTimeout(() => {
        if (this.copiedBotName === botName) {
          this.copiedBotName = null;
        }
      }, 2000);
    } catch {
      // Fallback or ignore if clipboard is not accessible
    }
  }
}
