export interface Product {
  id?: number;
  name: string;
  description?: string;
  price: number;
  stock: number;
}

export interface ApiResponseDTO<T> {
  ok: boolean;
  statusCode: number;
  message: string;
  data: T;
  count: number;
}
