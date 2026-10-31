export type OrderStatus =
  | 'CREADO'
  | 'ACEPTADO'
  | 'EN_PREPARACION'
  | 'DESPACHADO'
  | 'ENTREGADO'
  | 'CANCELADO';

export interface OrderItem {
  id?: number;
  productId: number;
  productName?: string;
  quantity: number;
  unitPrice?: number;
}

export interface Order {
  id?: number;
  customerId?: string;
  customerEmail?: string;
  status: OrderStatus;
  total?: number;
  createdAt?: string;
  updatedAt?: string;
  items: OrderItem[];
}

// Transiciones validas desde cada estado (debe coincidir con OrderService.java del backend)
export const NEXT_STATUS: Record<OrderStatus, OrderStatus[]> = {
  CREADO: ['ACEPTADO', 'CANCELADO'],
  ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['DESPACHADO', 'CANCELADO'],
  DESPACHADO: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
};
