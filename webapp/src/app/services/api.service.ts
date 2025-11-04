import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Customer, CustomerWithOrders, Order } from '../models';
import { ConfigService } from './config.service';

@Injectable({ providedIn: 'root' })
export class ApiService {

  private jsonHeaders = new HttpHeaders({ 'Content-Type': 'application/json' });

  constructor(
    private http: HttpClient,
    private config: ConfigService
  ) { }

  /** Dynamically fetch latest BASE URL from env.js */
  private get BASE(): string {
    return this.config.apiBaseUrl ? this.config.apiBaseUrl : 'http://localhost:8085';
  }

  // ------------------ Customers ------------------

  listCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.BASE}/customers`, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  createCustomer(c: { name: string; email: string }): Observable<Customer> {
    return this.http.post<Customer>(`${this.BASE}/customers`, c, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  getCustomerWithOrders(id: string): Observable<CustomerWithOrders> {
    return this.http.get<CustomerWithOrders>(`${this.BASE}/customers/${id}`, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  deleteCustomer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/customers/${id}`, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  // ------------------ Orders ------------------

  listOrders(customerId?: string): Observable<Order[]> {
    const url = customerId
      ? `${this.BASE}/orders?customerId=${encodeURIComponent(customerId)}`
      : `${this.BASE}/orders`;

    return this.http.get<Order[]>(url, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  createOrder(payload: { customerId: string; amount: number }): Observable<Order> {
    return this.http.post<Order>(`${this.BASE}/orders`, payload, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  // ------------------ Login ------------------

  login(email: string, password: string): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(
      `${this.BASE}/customers/login`,
      { email, password },
      {
        headers: this.jsonHeaders,
        withCredentials: true,
      }
    );
  }
}
