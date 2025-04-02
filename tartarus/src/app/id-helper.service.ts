import { Injectable } from '@angular/core';
import * as base32 from 'hi-base32';

@Injectable({
  providedIn: 'root'
})
export class IdHelperService {

  constructor() { }

  generateBase32Id(length: number = 6): string {
    // Generate a random number. Adjust the range as needed.
    let randomNumber = Math.floor(Math.random() * Math.pow(2, 24)); // 20 bits

    // Convert the number to a Base32 encoded string
    let encoded = base32.encode(randomNumber.toString(16));

    // Return the first 'length' characters
    return encoded.substring(0, length).toUpperCase();
  }
}
