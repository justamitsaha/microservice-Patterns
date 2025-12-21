import {
  HttpInterceptorFn,
  HttpRequest,
  HttpHandlerFn,
  HttpErrorResponse
} from '@angular/common/http';
import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ConfigService } from '../services/config.service';
import {
  catchError,
  switchMap,
  throwError,
  BehaviorSubject,
  filter,
  take
} from 'rxjs';

// Emits the latest refreshed access token to queued requests
// - null   → refresh in progress
// - string → new token available
const refreshSubject = new BehaviorSubject<string | null>(null);

// Global flag to ensure ONLY ONE refresh call runs at a time
let isRefreshing = false;

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('accessToken');
  const clientId = sessionStorage.getItem('clientId');

  const http = inject(HttpClient);
  const config = inject(ConfigService);

  const refreshUrl = `${config.apiBaseUrl}/customers/refresh`;

  // 1️⃣ Attach UUID first
  let modifiedReq = req;
  if (clientId) {
    modifiedReq = modifiedReq.clone({
      setHeaders: { 'X-Client-Id': clientId }
    });
  }

  // 2️⃣ Skip JWT logic for public endpoints
  if (req.url.includes('/login') || req.url.includes('/refresh')) {
    return next(modifiedReq);
  }

  // 3️⃣ Attach JWT if present
  const cloned = token
    ? modifiedReq.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    })
    : modifiedReq;

  return next(cloned).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {

        if (!isRefreshing) {
          isRefreshing = true;
          refreshSubject.next(null);

          return http.post<{ accessToken: string }>(
            refreshUrl,
            {},
            {
              withCredentials: true,
              headers: clientId ? { 'X-Client-Id': clientId } : {}
            }
          ).pipe(
            switchMap(res => {
              isRefreshing = false;
              localStorage.setItem('accessToken', res.accessToken);
              refreshSubject.next(res.accessToken);

              return next(
                cloned.clone({
                  setHeaders: {
                    Authorization: `Bearer ${res.accessToken}`
                  }
                })
              );
            }),
            catchError(err => {
              isRefreshing = false;
              localStorage.removeItem('accessToken');
              return throwError(() => err);
            })
          );
        }

        return refreshSubject.pipe(
          filter(t => t !== null),
          take(1),
          switchMap(t =>
            next(
              cloned.clone({
                setHeaders: { Authorization: `Bearer ${t}` }
              })
            )
          )
        );
      }

      return throwError(() => error);
    })
  );
};

