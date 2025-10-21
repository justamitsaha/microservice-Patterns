import { Routes } from '@angular/router';
import { CustomersComponent } from './pages/customers/customers.component';
import { OrdersComponent } from './pages/orders/orders.component';
import { CustomerDetailComponent } from './pages/customer-detail/customer-detail.component';
import { LoginComponent } from './pages/login/login.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'customers' },
  { path: 'login', component: LoginComponent },
  { path: 'customers', component: CustomersComponent },
  { path: 'customers/:id', component: CustomerDetailComponent },
  { path: 'orders', component: OrdersComponent },
  { path: '**', redirectTo: 'customers' }
];
