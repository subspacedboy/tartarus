import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BotSelectorComponent } from './bot-selector.component';
import { Bot } from '../models/bot';

describe('BotSelectorComponent', () => {
  let component: BotSelectorComponent;
  let fixture: ComponentFixture<BotSelectorComponent>;

  const mockBots: Bot[] = [
    new Bot({
      name: 'bot-b',
      description: 'Secondary runner',
      publicKey: 'pubkey-b',
      createdAt: '2026-03-01T10:00:00Z'
    }),
    new Bot({
      name: 'bot-a',
      description: 'Alpha processor',
      publicKey: 'pubkey-a',
      createdAt: '2026-01-01T10:00:00Z'
    }),
    new Bot({
      name: 'bot-c',
      description: 'Tertiary monitor',
      publicKey: 'pubkey-c',
      createdAt: '2026-05-01T10:00:00Z'
    })
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BotSelectorComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(BotSelectorComponent);
    component = fixture.componentInstance;
    component.bots = [...mockBots];
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should filter bots by name or description', () => {
    component.searchQuery = 'alpha';
    expect(component.filteredBots.length).toBe(1);
    expect(component.filteredBots[0].name).toBe('bot-a');

    component.searchQuery = 'runner';
    expect(component.filteredBots.length).toBe(1);
    expect(component.filteredBots[0].name).toBe('bot-b');

    component.searchQuery = 'nonexistent';
    expect(component.filteredBots.length).toBe(0);
  });

  it('should sort bots alphabetically by name asc/desc', () => {
    component.sortField = 'name';
    component.sortDirection = 'asc';
    expect(component.filteredBots.map(b => b.name)).toEqual(['bot-a', 'bot-b', 'bot-c']);

    component.toggleSort('name');
    expect(component.sortDirection).toBe('desc');
    expect(component.filteredBots.map(b => b.name)).toEqual(['bot-c', 'bot-b', 'bot-a']);
  });

  it('should sort bots by date added', () => {
    component.toggleSort('createdAt');
    expect(component.sortField).toBe('createdAt');
    // Initially toggled to createdAt defaults to desc (newest first)
    expect(component.sortDirection).toBe('desc');
    expect(component.filteredBots.map(b => b.name)).toEqual(['bot-c', 'bot-b', 'bot-a']);

    component.toggleSort('createdAt');
    expect(component.sortDirection).toBe('asc');
    expect(component.filteredBots.map(b => b.name)).toEqual(['bot-a', 'bot-b', 'bot-c']);
  });

  it('should correctly identify if a bot is selected', () => {
    component.selectedBotNames = ['bot-a'];
    expect(component.isBotSelected('bot-a')).toBe(true);
    expect(component.isBotSelected('bot-b')).toBe(false);
  });

  it('should emit botAdded when onAdd is called', () => {
    let emittedBot: Bot | undefined;
    component.botAdded.subscribe((bot) => {
      emittedBot = bot;
    });

    component.onAdd(mockBots[0]);
    expect(emittedBot).toBe(mockBots[0]);
  });

  it('should emit botRemoved when onRemove is called', () => {
    let removedName: string | undefined;
    component.botRemoved.subscribe((name) => {
      removedName = name;
    });

    component.onRemove('bot-a');
    expect(removedName).toBe('bot-a');
  });
});
