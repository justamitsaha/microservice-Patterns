import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  email = '';
  password = '';
  message = '';

  constructor(private api: ApiService) { }

  submit() {
    const email = this.email.trim();
    const password = this.password.trim();
    if (!email || !password) return;
    this.api.login(email, password).subscribe({
      next: (res: any) => {
        localStorage.setItem('accessToken', res.accessToken);
      },
      error: err => this.message = err.message
    });
  }
}

