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
  isLoginMode = true;
  name = '';
  email = '';
  password = '';
  message = '';

  constructor(private api: ApiService) { }

  toggleMode() {
    this.isLoginMode = !this.isLoginMode;
    this.message = '';
  }

  submit() {
    if (this.isLoginMode) {
      this.login();
    } else {
      this.register();
    }
  }

  private login() {
    const email = this.email.trim();
    const password = this.password.trim();
    if (!email || !password) return;
    this.api.login(email, password).subscribe({
      next: (res: any) => {
        localStorage.setItem('accessToken', res.accessToken);
        this.message = 'Login successful!';
      },
      error: err => this.message = err.message
    });
  }

  private register() {
    const name = this.name.trim();
    const email = this.email.trim();
    const password = this.password.trim();
    if (!name || !email || !password) return;
    this.api.createCustomer({ name, email, password } as any).subscribe({
      next: _ => {
        this.message = 'Registration successful! Please login.';
        this.isLoginMode = true;
        this.name = '';
      },
      error: err => this.message = err.message
    });
  }
}

