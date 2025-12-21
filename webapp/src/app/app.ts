import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationService } from './services/notification.service';
import { AuthService } from './services/auth.service';
import { ApiService } from './services/api.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('webapp');
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);

  notice = this.notifications.notice;
  unauth = this.auth.unauthenticated;

  constructor(private api: ApiService) { }


  ngOnInit(): void {
    this.api.init().subscribe(res => {
      sessionStorage.setItem('clientId', res.clientId);
    });
  }
}
