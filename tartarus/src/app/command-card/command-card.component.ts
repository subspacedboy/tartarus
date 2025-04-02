import {Component, Input} from '@angular/core';
import {Contract} from '../models/contract';
import {Command} from '../models/command';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-command-card',
  imports: [
    DatePipe,
    RouterLink
  ],
  templateUrl: './command-card.component.html',
  styleUrl: './command-card.component.scss'
})
export class CommandCardComponent {
  @Input()
  command?: Command;

  @Input() basePath: string = '';
}
