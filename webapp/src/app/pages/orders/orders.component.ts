import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Order } from '../../models';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './orders.component.html'
})
export class OrdersComponent implements OnInit {
  orders: Order[] = [];
  filterCustomerId = '';
  customerId = '';
  amount: number | null = null;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load() {
    const cid = this.filterCustomerId.trim();
    this.api.listOrders(cid || undefined).subscribe({
      next: os => { this.orders = os; this.error = ''; },
      error: err => this.error = err.message
    });
  }

  create() {
    if (!this.customerId || !this.amount || this.amount <= 0) return;
    this.api.createOrder({ customerId: this.customerId, amount: this.amount }).subscribe({
      next: _ => { this.customerId=''; this.amount=null; this.load(); },
      error: err => this.error = err.message
    });
  }
}

