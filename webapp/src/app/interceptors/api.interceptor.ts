import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { NotificationService } from '../services/notification.service';
import { AuthService } from '../services/auth.service';

export const apiInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> => {
  const notify = inject(NotificationService);
  const auth = inject(AuthService);

  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          // Show success for mutating requests
          const method = req.method.toUpperCase();
          if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
            notify.success(`${method} ${req.url} succeeded`);
          }
          // Clear unauth flag on successful responses
          auth.clearUnauthenticated();
        }
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          if (err.status === 401) {
            auth.flagUnauthenticated();
            notify.error(`Unauthorized (401) on ${req.url}`, 401);
          } else {
            const msg = err.error?.message || err.message || 'Request failed';
            notify.error(`${msg}`, err.status);
          }
        } else {
          notify.error('Unexpected error');
        }
      }
    })
  );
};

