import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Customer, CustomerWithOrders, Order } from '../models';

const BASE = '/api';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  listCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${BASE}/customers`);
  }
  createCustomer(c: { name: string; email: string }): Observable<Customer> {
    return this.http.post<Customer>(`${BASE}/customers`, c);
  }
  getCustomerWithOrders(id: string): Observable<CustomerWithOrders> {
    return this.http.get<CustomerWithOrders>(`${BASE}/customers/${id}`);
  }
  deleteCustomer(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/customers/${id}`);
  }

  listOrders(customerId?: string): Observable<Order[]> {
    const url = customerId ? `${BASE}/orders?customerId=${encodeURIComponent(customerId)}` : `${BASE}/orders`;
    return this.http.get<Order[]>(url);
  }
  createOrder(payload: { customerId: string; amount: number }): Observable<Order> {
    return this.http.post<Order>(`${BASE}/orders`, payload);
  }
}

