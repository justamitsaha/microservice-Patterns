import { Injectable, signal } from '@angular/core';

export type NoticeType = 'success' | 'error' | 'info';

export interface Notice {
  type: NoticeType;
  text: string;
  status?: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  notice = signal<Notice | null>(null);

  success(text: string) {
    this.notice.set({ type: 'success', text });
    this.autoClear();
  }
  error(text: string, status?: number) {
    this.notice.set({ type: 'error', text, status });
  }
  info(text: string) {
    this.notice.set({ type: 'info', text });
    this.autoClear();
  }
  clear() {
    this.notice.set(null);
  }
  private autoClear() {
    setTimeout(() => this.clear(), 3500);
  }
}

