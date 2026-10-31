import { apiClient } from './apiClient';
import type { ApiResponseDTO } from '../types/product';
import type { Order, OrderStatus } from '../types/order';

const BASE = '/api/orders';

export const getOrders = async (): Promise<Order[]> => {
  const res = await apiClient.get<ApiResponseDTO<Order[]>>(BASE);
  return res.data.data || [];
};

export const createOrder = async (items: { productId: number; quantity: number }[]): Promise<Order> => {
  const res = await apiClient.post<ApiResponseDTO<Order>>(BASE, { items });
  return res.data.data;
};

export const changeOrderStatus = async (id: number, status: OrderStatus): Promise<Order> => {
  const res = await apiClient.put<ApiResponseDTO<Order>>(`${BASE}/${id}/status`, { status });
  return res.data.data;
};
