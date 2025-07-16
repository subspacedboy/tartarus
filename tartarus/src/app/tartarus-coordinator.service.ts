import { Injectable } from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import {catchError, map, Observable, throwError} from 'rxjs';
import * as base32 from 'hi-base32';
import {LockSession} from './models/lock-session';
import {AuthorSession} from './models/author-session';
import {Contract} from './models/contract';
import {ConfigService} from './config.service';
import {AppConfig} from './models/app-config';
import {KnownToken} from './models/known-token';
import {ToastService} from './toast.service';
import {Command} from './models/command';
import {Bot} from './models/bot';
import {AdminSession} from './models/admin-session';
import {LockUserSession} from './models/lock-user-session';

@Injectable({
  providedIn: 'root'
})
export class TartarusCoordinatorService {
  private readonly baseUrl;

  constructor(
    private http: HttpClient,
    private configService: ConfigService,
    private toastService: ToastService,) {
    let config = this.configService.getConfig();
    this.baseUrl = String(config.apiUri);
  }

  public saveKeyRecord(public_key: string) : Observable<boolean> {
    const save_key_uri = `${this.baseUrl}/keys`;
    const body = JSON.stringify({
      'public_key' : public_key
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_key_uri, body, {headers} ).pipe(map((res:any) => {
      return true;
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  // Lock sessions
  public getLockSession(sessionToken: string) : Observable<LockSession> {
    const get_lock_session_uri = `${this.baseUrl}/lock_sessions/${sessionToken}`;
    return this.http.get(get_lock_session_uri, {
      headers: new HttpHeaders({ 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      return new LockSession(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getMyLockSession() : Observable<LockSession> {
    const get_my_lock_session_uri = `${this.baseUrl}/lock_sessions/mine`;
    return this.http.get(get_my_lock_session_uri, {
      headers: new HttpHeaders({ 'X-Require-LockUser': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      return new LockSession(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getKnownTokensForAuthor() : Observable<KnownToken[]> {
    const get_tokens_uri = `${this.baseUrl}/lock_sessions/known`;
    return this.http.get(get_tokens_uri, {
      headers: new HttpHeaders({ 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const knownTokens : KnownToken[] = res.map((datum: any) => {
        return new KnownToken(datum);
      });

      return knownTokens;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveNotesForKnownToken(knownToken : string, notes: string) : Observable<boolean> {
    const save_notes_uri = `${this.baseUrl}/lock_sessions/known/${knownToken}`;
    const body = JSON.stringify({
      'name' : knownToken,
      'notes' : notes,
    });
    return this.http.put(save_notes_uri, body, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      return true;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveLockUserSessionWithCryptogram(nonce: string, cipher: string, session: string, userSessionPublicPem: string) : Observable<LockUserSession> {
      const save_key_uri = `${this.baseUrl}/lock_user_sessions/`;
      const body = JSON.stringify({
        'lockUserSessionPublicKey' : userSessionPublicPem,
        'sessionToken' : session,
        'cipher' : cipher,
        'nonce' : nonce,
      });
      const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
      return this.http.post(save_key_uri, body, {headers} ).pipe(map((res:any) => {
        return new LockUserSession(res);
      }),  catchError(error => {
        return this.handleError(error);
      }));
    }

  // Author sessions
  public createAuthorSession(public_key: string, session_token: string, signature : string): Observable<AuthorSession> {
    const save_key_uri = `${this.baseUrl}/author_sessions/`;
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

  // Contracts & Commands

  public saveContract(authorName: string, shareableToken: string, signed_message: Uint8Array, notes : string) : Observable<Contract> {
    const save_contract_uri = `${this.baseUrl}/contracts/`;
    const body = JSON.stringify({
      'shareableToken' : shareableToken,
      'authorName' : authorName,
      'signedMessage' : btoa(String.fromCharCode(...signed_message)),
      'notes' : notes,
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-Author' : 'require authorization tokens' });
    return this.http.post(save_contract_uri, body, {headers} ).pipe(map((res:any) => {
      // let t = new Ticket(res);
      return new Contract(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractsForShareable(shareableToken: string) : Observable<Contract[]> {
    const get_contracts_uri = `${this.baseUrl}/contracts/byShareable/${shareableToken}`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const contracts : Contract[] = res.map((datum: any) => {
        return new Contract(datum);
      });

      return contracts;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractByNameForAuthor(name: string) : Observable<Contract> {
    const get_contracts_uri = `${this.baseUrl}/contracts/${name}`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      return new Contract(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractByNameForLockUser(name: string) : Observable<Contract> {
    const get_contracts_uri = `${this.baseUrl}/contracts/${name}`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-LockUser': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      return new Contract(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public saveCommand(contractName: string, authorName: string, shareableToken: string, signed_message: Uint8Array) : Observable<boolean> {
    const save_command_uri = `${this.baseUrl}/contracts/command`;
    const body = JSON.stringify({
      'shareableToken' : shareableToken,
      'authorSessionName' : authorName,
      'contractName': contractName,
      'signedMessage' : btoa(String.fromCharCode(...signed_message))
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-Author': 'requires authorization tokens' });
    return this.http.post(save_command_uri, body, {headers} ).pipe(map((res:any) => {
      return true;
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractsForLockSession() : Observable<Contract[]> {
    const get_contracts_uri = `${this.baseUrl}/contracts/byLockUserSession`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-LockUser': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const contracts : Contract[] = res.map((datum: any) => {
        return new Contract(datum);
      });

      return contracts;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractsForLockSessionForReview() : Observable<Contract[]> {
    const get_contracts_uri = `${this.baseUrl}/contracts/reviewPending`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-LockUser': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const contracts : Contract[] = res.map((datum: any) => {
        return new Contract(datum);
      });

      return contracts;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getConfirmedContractsForAuthor() : Observable<Contract[]> {
    const get_contracts_uri = `${this.baseUrl}/contracts/confirmed`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const contracts : Contract[] = res.map((datum: any) => {
        return new Contract(datum);
      });

      return contracts;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public approveContract(contractName: string) : Observable<Contract> {
    const approve_uri = `${this.baseUrl}/contracts/approve/${contractName}`;
    const body = JSON.stringify({
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-LockUser': 'requires authorization tokens' });
    return this.http.post(approve_uri, body, {headers} ).pipe(map((res:any) => {
      return new Contract(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public rejectContract(contractName: string) : Observable<Contract> {
    const reject_uri = `${this.baseUrl}/contracts/reject/${contractName}`;
    const body = JSON.stringify({
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-LockUser': 'requires authorization tokens' });
    return this.http.post(reject_uri, body, {headers} ).pipe(map((res:any) => {
      return new Contract(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public getCommandsForContractForAuthor(contractName: string) : Observable<Command[]> {
    const get_contracts_uri = `${this.baseUrl}/contracts/${contractName}/commands/forAuthor`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Author': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const commands : Command[] = res.map((datum: any) => {
        return new Command(datum);
      });

      return commands;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getCommandsForContractForLockUser(contractName: string) : Observable<Command[]> {
    const get_contracts_uri = `${this.baseUrl}/contracts/${contractName}/commands/forLockUser`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-LockUser': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const commands : Command[] = res.map((datum: any) => {
        return new Command(datum);
      });

      return commands;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public manuallyAcknowledgeCommand(contractName: string, commandName: string) : Observable<Command> {
    const acknowledge_uri = `${this.baseUrl}/contracts/${contractName}/commands/forLockUser/command/${commandName}/acknowledge`;
    const body = JSON.stringify({
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-LockUser': 'requires authorization tokens' });
    return this.http.post(acknowledge_uri, body, {headers} ).pipe(map((res:any) => {
      return new Command(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  // Configuration data
  public getConfigurationFromCoordinator() : Observable<AppConfig> {
    const get_configuration_uri = `${this.baseUrl}/configuration/`;
    return this.http.get(get_configuration_uri, {
    }).pipe(map((res:any) => {
      return new AppConfig(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  // Bots
  public getBots() : Observable<Bot[]> {
    const get_bots_uri = `${this.baseUrl}/bots/`;
    return this.http.get(get_bots_uri, {
      // headers: new HttpHeaders({ 'X-Require-LockUser': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const bots : Bot[] = res.map((datum: any) => {
        return new Bot(datum);
      });

      return bots;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  // Admin sessions

  public confirmAdminSession(public_key: string, session_token: string, signature : string) : Observable<AdminSession> {
    const save_key_uri = `${this.baseUrl}/admin/admin_sessions/`;
    const body = JSON.stringify({
      'publicKey' : public_key,
      'sessionToken': session_token,
      'signature': signature
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post(save_key_uri, body, {headers} ).pipe(map((res:any) => {
      return new AdminSession(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  // Admin functions
  public getLiveContractsForAdmin(states : string[]) : Observable<Contract[]> {
    const queryParams = { states: states.join(',') };

    const get_contracts_uri = `${this.baseUrl}/admin/contracts/`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Admin': 'requires authorization tokens' }),
      params: queryParams,
    }).pipe(map((res:any) => {
      const contracts : Contract[] = res.map((datum: any) => {
        return new Contract(datum);
      });

      return contracts;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getContractByNameForAdmin(name: string) : Observable<Contract> {
    const get_contracts_uri = `${this.baseUrl}/admin/contracts/${name}`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Admin': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      return new Contract(res);
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public getCommandsForContractForAdmin(contractName: string) : Observable<Command[]> {
    const get_contracts_uri = `${this.baseUrl}/admin/contracts/${contractName}/commands/`;
    return this.http.get(get_contracts_uri, {
      headers: new HttpHeaders({ 'X-Require-Admin': 'requires authorization tokens' }),
    }).pipe(map((res:any) => {
      const commands : Command[] = res.map((datum: any) => {
        return new Command(datum);
      });

      return commands;
    }), catchError(error => {
      return this.handleError(error);
    }));
  }

  public abortContract(contractName: string) : Observable<Contract> {
    const reject_uri = `${this.baseUrl}/admin/contracts/${contractName}/abort`;
    const body = JSON.stringify({
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-Admin': 'requires authorization tokens' });
    return this.http.post(reject_uri, body, {headers} ).pipe(map((res:any) => {
      return new Contract(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  public fullReset(contractName: string) : Observable<Contract> {
    const reset_uri = `${this.baseUrl}/admin/contracts/${contractName}/reset`;
    const body = JSON.stringify({
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-Admin': 'requires authorization tokens' });
    return this.http.post(reset_uri, body, {headers} ).pipe(map((res:any) => {
      return new Contract(res);
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  // Admin firmware

  public addFirmware(firmwareBytes : string) : Observable<boolean> {
    const add_firmware_uri = `${this.baseUrl}/admin/firmware/`;
    const body = JSON.stringify({
      firmware : firmwareBytes,
    });
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', 'X-Require-Admin': 'requires authorization tokens' });
    return this.http.post(add_firmware_uri, body, {headers} ).pipe(map((res:any) => {
      return true;
    }),  catchError(error => {
      return this.handleError(error);
    }));
  }

  // Error handling

  private handleError(error: HttpErrorResponse) {
    if (error.error instanceof ErrorEvent) {
      // A client-side or network error occurred. Handle it accordingly.
      console.error('An error occurred:', error.error.message);
      this.toastService.showError('An error occurred: ' + error.error.message);
    } else {
      // The backend returned an unsuccessful response code.
      // The response body may contain clues as to what went wrong.

      // console.log(this.toastService);
      this.toastService.showError('The coordinator appears to be down...');
      console.error(
        `Backend returned code ${error.status}, ` +
        `body was: ${error.error}`);
    }
    this.toastService.showError(`Backend returned code ${error.status}, ` +
      `body was: ${error.error}`);
    // Return an observable with a user-facing error message.
    return throwError(() => error);
  }
}
