export interface Customer {
  id?: string;
  name: string;
  email: string;
  createdAt?: number;
}

export interface Order {
  orderId: string;
  customerId: string;
  amount: number;
  status: string;
}

export interface CustomerWithOrders extends Customer {
  orders?: Order[];
}

