import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Customer, CustomerWithOrders, Order } from '../models';

// ✅ Always point to API Gateway
// For local dev: use http://localhost:8085
// For production: configure in environment.ts
const BASE = 'http://localhost:8085';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) { }

  // Common headers (optional — helps prevent CORS surprises)
  private jsonHeaders = new HttpHeaders({
    'Content-Type': 'application/json'
  });

  // ------------------ Customers ------------------

  listCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${BASE}/customers`, {
      headers: this.jsonHeaders,
      withCredentials: true, // ✅ include if gateway allows credentials
    });
  }

  createCustomer(c: { name: string; email: string }): Observable<Customer> {
    return this.http.post<Customer>(`${BASE}/customers`, c, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  getCustomerWithOrders(id: string): Observable<CustomerWithOrders> {
    return this.http.get<CustomerWithOrders>(`${BASE}/customers/${id}`, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  deleteCustomer(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/customers/${id}`, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  // ------------------ Orders ------------------

  listOrders(customerId?: string): Observable<Order[]> {
    const url = customerId
      ? `${BASE}/orders?customerId=${encodeURIComponent(customerId)}`
      : `${BASE}/orders`;

    return this.http.get<Order[]>(url, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  createOrder(payload: { customerId: string; amount: number }): Observable<Order> {
    return this.http.post<Order>(`${BASE}/orders`, payload, {
      headers: this.jsonHeaders,
      withCredentials: true,
    });
  }

  // ------------------ Login ------------------

  login(email: string, password: string): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(
      `${BASE}/customers/login`,
      { email, password },
      {
        headers: this.jsonHeaders,
        withCredentials: true,
      }
    );
  }
}
