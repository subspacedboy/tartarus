import {Component, Input} from '@angular/core';
import {Contract} from '../models/contract';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-contract-card',
  imports: [
    RouterLink
  ],
  templateUrl: './contract-card.component.html',
  styleUrl: './contract-card.component.scss'
})
export class ContractCardComponent {
  @Input()
  contract?: Contract;
}
