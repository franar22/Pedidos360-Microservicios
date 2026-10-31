import { useEffect, useState } from 'react';
import { useHasRole } from '../auth/useRoles';
import { getOrders, createOrder, changeOrderStatus } from '../services/orderService';
import { getProducts } from '../services/catalogService';
import type { Order, OrderStatus } from '../types/order';
import { NEXT_STATUS } from '../types/order';
import type { Product } from '../types/product';

interface DraftItem {
  productId: number;
  quantity: number;
}

export function OrdersPage() {
  const isStaff = useHasRole('ADMIN') || useHasRole('OPERADOR');

  const [orders, setOrders] = useState<Order[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [draftItems, setDraftItems] = useState<DraftItem[]>([{ productId: 0, quantity: 1 }]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [ordersData, productsData] = await Promise.all([getOrders(), getProducts()]);
      setOrders(ordersData);
      setProducts(productsData);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const addDraftRow = () => setDraftItems([...draftItems, { productId: 0, quantity: 1 }]);

  const updateDraftRow = (index: number, field: keyof DraftItem, value: number) => {
    const copy = [...draftItems];
    copy[index] = { ...copy[index], [field]: value };
    setDraftItems(copy);
  };

  const removeDraftRow = (index: number) => {
    setDraftItems(draftItems.filter((_, i) => i !== index));
  };

  const handleCreateOrder = async () => {
    setError('');
    setSuccess('');
    const validItems = draftItems.filter((i) => i.productId > 0 && i.quantity > 0);

    if (!validItems.length) {
      setError('Agrega al menos un producto valido al pedido.');
      return;
    }

    try {
      await createOrder(validItems);
      setSuccess('Pedido creado satisfactoriamente.');
      setDraftItems([{ productId: 0, quantity: 1 }]);
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleChangeStatus = async (order: Order, newStatus: OrderStatus) => {
    setError('');
    setSuccess('');
    try {
      await changeOrderStatus(order.id!, newStatus);
      setSuccess(`Pedido #${order.id} actualizado a ${newStatus}.`);
      await loadData();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div className="mt-4">
      <h2>Pedidos</h2>

      {error && <div className="alert alert-danger">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      {/* Crear pedido: disponible para todos los roles (Cliente, Operador, Admin) */}
      <div className="card mb-4">
        <div className="card-body">
          <h5 className="card-title">Nuevo pedido</h5>
          {draftItems.map((item, index) => (
            <div className="row g-2 align-items-center mb-2" key={index}>
              <div className="col-6">
                <select
                  className="form-select"
                  value={item.productId}
                  onChange={(e) => updateDraftRow(index, 'productId', Number(e.target.value))}
                >
                  <option value={0}>-- Selecciona un producto --</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} (stock: {p.stock})
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-3">
                <input
                  type="number"
                  min={1}
                  className="form-control"
                  value={item.quantity}
                  onChange={(e) => updateDraftRow(index, 'quantity', Number(e.target.value))}
                />
              </div>
              <div className="col-3">
                <button
                  className="btn btn-outline-danger btn-sm"
                  onClick={() => removeDraftRow(index)}
                  disabled={draftItems.length === 1}
                >
                  Quitar
                </button>
              </div>
            </div>
          ))}
          <button className="btn btn-outline-secondary btn-sm me-2" onClick={addDraftRow}>
            + Agregar producto
          </button>
          <button className="btn btn-primary btn-sm" onClick={handleCreateOrder}>
            Crear pedido
          </button>
        </div>
      </div>

      {loading ? (
        <p>Cargando pedidos...</p>
      ) : (
        <table className="table table-striped">
          <thead>
            <tr>
              <th>#</th>
              <th>Cliente</th>
              <th>Items</th>
              <th>Total</th>
              <th>Estado</th>
              {isStaff && <th>Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td>{order.id}</td>
                <td>{order.customerEmail || order.customerId}</td>
                <td>
                  {order.items.map((i) => `${i.productName} x${i.quantity}`).join(', ')}
                </td>
                <td>${order.total?.toLocaleString('es-CL')}</td>
                <td>
                  <span className="badge bg-secondary">{order.status}</span>
                </td>
                {isStaff && (
                  <td>
                    {NEXT_STATUS[order.status].map((next) => (
                      <button
                        key={next}
                        className="btn btn-sm btn-outline-primary me-1 mb-1"
                        onClick={() => handleChangeStatus(order, next)}
                      >
                        {next}
                      </button>
                    ))}
                  </td>
                )}
              </tr>
            ))}
            {!orders.length && (
              <tr>
                <td colSpan={isStaff ? 6 : 5} className="text-center text-muted">
                  No hay pedidos todavia.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}

function getErrorMessage(err: unknown): string {
  if (err && typeof err === 'object' && 'response' in err) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const response = (err as any).response;
    return response?.data?.message || 'Error al conectar con la API.';
  }
  return 'Error al conectar con la API.';
}
