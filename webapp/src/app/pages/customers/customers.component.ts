import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Customer } from '../../models';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './customers.component.html'
})
export class CustomersComponent implements OnInit {
  customers: Customer[] = [];
  name = '';
  email = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load() {
    this.api.listCustomers().subscribe({
      next: cs => { this.customers = cs; this.error = ''; },
      error: err => this.error = err.message
    });
  }

  create() {
    const name = this.name.trim();
    const email = this.email.trim();
    if (!name || !email) return;
    this.api.createCustomer({ name, email }).subscribe({
      next: _ => { this.name = ''; this.email = ''; this.load(); },
      error: err => this.error = err.message
    });
  }

  delete(id: string) {
    this.api.deleteCustomer(id).subscribe({
      next: _ => this.load(),
      error: err => this.error = err.message
    });
  }
}

