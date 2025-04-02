import { Injectable } from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import {catchError, map, Observable, throwError} from 'rxjs';
import * as base32 from 'hi-base32';
import {LockSession} from './models/lock-session';
import {AuthorSession} from './models/author-session';
import {Contract} from './models/contract';

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

  public getLockSession(sessionToken: string) : Observable<LockSession> {
    const get_lock_session_uri = `http://localhost:5002/lock_sessions/${sessionToken}`;
    return this.http.get(get_lock_session_uri, {
    }).pipe(map((res:any) => {
      return new LockSession(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getMyLockSession(sessionToken: string) : Observable<LockSession> {
    const get_my_lock_session_uri = `http://localhost:5002/lock_sessions/mine/${sessionToken}`;
    return this.http.get(get_my_lock_session_uri, {
    }).pipe(map((res:any) => {
      return new LockSession(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public createAuthorSession(public_key: string, session_token: string, signature : string): Observable<AuthorSession> {
    const save_key_uri = 'http://localhost:5002/author_sessions/';
    const body = JSON.stringify({
      'publicKey' : public_key,
      'sessionToken': session_token,
      'signature': signature
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_key_uri, body, {headers} ).pipe(map((res:any) => {
      return new AuthorSession(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveLockPubKeyAndSession(public_key: string, session: string) : Observable<LockSession> {
    const save_key_uri = 'http://localhost:5002/lock_sessions/';
    const body = JSON.stringify({
      'publicKey' : public_key,
      'sessionToken' : session
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_key_uri, body, {headers} ).pipe(map((res:any) => {
      return new LockSession(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveContract(authorName: string, shareableToken: string, signed_message: Uint8Array) : Observable<boolean> {
    const save_contract_uri = `http://localhost:5002/contracts/`;
    const body = JSON.stringify({
      'shareableToken' : shareableToken,
      'authorName' : authorName,
      'signedMessage' : btoa(String.fromCharCode(...signed_message))
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_contract_uri, body, {headers} ).pipe(map((res:any) => {
      // let t = new Ticket(res);
      return true;
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractsForShareable(shareableToken: string) : Observable<Contract[]> {
    const get_contracts_uri = `http://localhost:5002/contracts/byShareable/${shareableToken}`;
    return this.http.get(get_contracts_uri, {
    }).pipe(map((res:any) => {
      // const contracts : Contract[] = res['contracts'].map((datum: any) => {
      //   return new Contract(datum);
      // });
      const contracts : Contract[] = res.map((datum: any) => {
        return new Contract(datum);
      });

      return contracts;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractByName(name: string) : Observable<Contract> {
    const get_contracts_uri = `http://localhost:5002/contracts/${name}`;
    return this.http.get(get_contracts_uri, {
    }).pipe(map((res:any) => {
      return new Contract(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveUnlock(contractName: string, authorName: string, shareableToken: string, signed_message: Uint8Array) : Observable<boolean> {
    const save_command_uri = `http://localhost:5002/contracts/command`;
    const body = JSON.stringify({
      'shareableToken' : shareableToken,
      'authorSessionName' : authorName,
      'contractName': contractName,
      'signedMessage' : btoa(String.fromCharCode(...signed_message))
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_command_uri, body, {headers} ).pipe(map((res:any) => {
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
