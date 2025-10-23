import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, switchMap, throwError, BehaviorSubject, filter, take } from 'rxjs';

const refreshSubject = new BehaviorSubject<string | null>(null);
let isRefreshing = false;

export const jwtInterceptor: HttpInterceptorFn = (req: HttpRequest<any>, next: HttpHandlerFn) => {
  const token = localStorage.getItem('accessToken');
  const http = inject(HttpClient);

  // Skip auth for login and refresh endpoints
  if (req.url.includes('/login') || req.url.includes('/refresh')) {
    return next(req);
  }

  // Add token header if present
  const cloned = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(cloned).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        if (!isRefreshing) {
          isRefreshing = true;
          refreshSubject.next(null);
          return http.post<{ accessToken: string }>(
            'http://localhost:8085/customers/refresh',
            {},
            { withCredentials: true }
          ).pipe(
            switchMap(res => {
              isRefreshing = false;
              const newToken = res.accessToken;
              localStorage.setItem('accessToken', newToken);
              refreshSubject.next(newToken);
              const retryReq = cloned.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } });
              return next(retryReq);
            }),
            catchError(err => {
              isRefreshing = false;
              localStorage.removeItem('accessToken');
              return throwError(() => err);
            })
          );
        } else {
          return refreshSubject.pipe(
            filter(t => t !== null),
            take(1),
            switchMap(t => next(cloned.clone({ setHeaders: { Authorization: `Bearer ${t}` } })))
          );
        }
      }
      return throwError(() => error);
    })
  );
};
