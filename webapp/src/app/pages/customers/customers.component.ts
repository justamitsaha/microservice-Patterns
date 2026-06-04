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
  error = '';

  editingId: string | null = null;
  editName = '';
  editEmail = '';

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

  startEdit(c: Customer) {
    this.editingId = c.id!;
    this.editName = c.name;
    this.editEmail = c.email;
  }

  cancelEdit() {
    this.editingId = null;
  }

  saveEdit() {
    if (!this.editingId) return;
    this.api.updateCustomer(this.editingId, { name: this.editName, email: this.editEmail }).subscribe({
      next: _ => {
        this.editingId = null;
        this.load();
      },
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
