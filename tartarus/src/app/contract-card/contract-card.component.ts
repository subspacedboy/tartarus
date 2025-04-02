import {Component, Input} from '@angular/core';
import {Contract} from '../models/contract';
import {RouterLink} from '@angular/router';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-contract-card',
  imports: [
    RouterLink,
    DatePipe
  ],
  templateUrl: './contract-card.component.html',
  styleUrl: './contract-card.component.scss'
})
export class ContractCardComponent {
  @Input()
  contract?: Contract;

  @Input() basePath: string = '';
}
