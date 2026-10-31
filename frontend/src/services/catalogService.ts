import { apiClient } from './apiClient';
import type { ApiResponseDTO, Product } from '../types/product';

const BASE = '/api/catalog/products';

export const getProducts = async (): Promise<Product[]> => {
  const res = await apiClient.get<ApiResponseDTO<Product[]>>(BASE);
  return res.data.data || [];
};

export const createProduct = async (product: Product): Promise<Product> => {
  const res = await apiClient.post<ApiResponseDTO<Product>>(BASE, product);
  return res.data.data;
};

export const updateProduct = async (id: number, product: Product): Promise<Product> => {
  const res = await apiClient.put<ApiResponseDTO<Product>>(`${BASE}/${id}`, product);
  return res.data.data;
};

export const updateStock = async (id: number, stock: number): Promise<Product> => {
  const res = await apiClient.patch<ApiResponseDTO<Product>>(`${BASE}/${id}/stock`, { stock });
  return res.data.data;
};

export const deleteProduct = async (id: number): Promise<void> => {
  await apiClient.delete(`${BASE}/${id}`);
};
