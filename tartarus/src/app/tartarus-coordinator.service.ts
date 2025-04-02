import { Injectable } from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import {catchError, map, Observable, throwError} from 'rxjs';
import * as base32 from 'hi-base32';

@Injectable({
  providedIn: 'root'
})
export class TartarusCoordinatorService {

  constructor(private http: HttpClient) {
  }

  public saveKeyRecord(public_key: string) : Observable<boolean> {
    const save_key_uri = 'http://localhost:5002/keys';
    const body = JSON.stringify({
      'public_key' : public_key
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_key_uri, body, {headers} ).pipe(map((res:any) => {
      // let t = new Ticket(res);
      return true;
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveContract(name: string, signed_message: Uint8Array) : Observable<boolean> {
    const save_contract_uri = `http://localhost:5002/contracts/${name}`;
    const body = JSON.stringify({
      'signed_message' : btoa(String.fromCharCode(...signed_message))
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_contract_uri, body, {headers} ).pipe(map((res:any) => {
      // let t = new Ticket(res);
      return true;
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  private handleError(error: HttpErrorResponse) {
    if (error.error instanceof ErrorEvent) {
      // A client-side or network error occurred. Handle it accordingly.
      console.error('An error occurred:', error.error.message);
      // this.toastService.showError('An error occurred: ' + error.error.message);
    } else {
      // The backend returned an unsuccessful response code.
      // The response body may contain clues as to what went wrong.

      // console.log(this.toastService);
      // this.toastService.showError('The contract manager appears to be down...');
      console.error(
        `Backend returned code ${error.status}, ` +
        `body was: ${error.error}`);
    }
    // this.toastService.showError(`Backend returned code ${error.status}, ` +
    //   `body was: ${error.error}`);
    // Return an observable with a user-facing error message.
    return throwError(() => error);
  }
}
