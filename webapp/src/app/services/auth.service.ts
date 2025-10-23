import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  unauthenticated = signal(false);

  flagUnauthenticated() {
    this.unauthenticated.set(true);
  }
  clearUnauthenticated() {
    this.unauthenticated.set(false);
  }
}

