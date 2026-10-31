import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useHasRole } from '../auth/useRoles';
import {
  getProducts,
  createProduct,
  updateProduct,
  updateStock,
  deleteProduct,
} from '../services/catalogService';
import type { Product } from '../types/product';

const emptyForm: Product = { name: '', description: '', price: 0, stock: 0 };

export function CatalogPage() {
  const isAdmin = useHasRole('ADMIN');
  const isOperador = useHasRole('OPERADOR');
  const canManageStock = isAdmin || isOperador;

  const [products, setProducts] = useState<Product[]>([]);
  const [formData, setFormData] = useState<Product>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loadProducts();
  }, []);

  const loadProducts = async () => {
    try {
      setLoading(true);
      setProducts(await getProducts());
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      if (editingId) {
        await updateProduct(editingId, formData);
        setSuccess('Producto actualizado.');
      } else {
        await createProduct(formData);
        setSuccess('Producto creado.');
      }
      setFormData(emptyForm);
      setEditingId(null);
      await loadProducts();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleEdit = (product: Product) => {
    setEditingId(product.id!);
    setFormData(product);
  };

  const handleDelete = async (id: number) => {
    setError('');
    setSuccess('');
    try {
      await deleteProduct(id);
      setSuccess('Producto eliminado.');
      await loadProducts();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleStockChange = async (id: number, newStock: number) => {
    setError('');
    try {
      await updateStock(id, newStock);
      await loadProducts();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div className="mt-4">
      <h2>Catalogo</h2>

      {error && <div className="alert alert-danger">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      {/* Crear / editar producto completo: solo ADMIN */}
      {isAdmin && (
        <form className="card card-body mb-4" onSubmit={handleSubmit}>
          <h5>{editingId ? 'Editar producto' : 'Nuevo producto'}</h5>
          <div className="row g-2">
            <div className="col-md-4">
              <input
                className="form-control"
                placeholder="Nombre"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              />
            </div>
            <div className="col-md-4">
              <input
                className="form-control"
                placeholder="Descripcion"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              />
            </div>
            <div className="col-md-2">
              <input
                type="number"
                className="form-control"
                placeholder="Precio"
                required
                value={formData.price}
                onChange={(e) => setFormData({ ...formData, price: Number(e.target.value) })}
              />
            </div>
            <div className="col-md-2">
              <input
                type="number"
                className="form-control"
                placeholder="Stock"
                required
                value={formData.stock}
                onChange={(e) => setFormData({ ...formData, stock: Number(e.target.value) })}
              />
            </div>
          </div>
          <div className="mt-2">
            <button type="submit" className="btn btn-primary btn-sm me-2">
              {editingId ? 'Guardar cambios' : 'Crear producto'}
            </button>
            {editingId && (
              <button
                type="button"
                className="btn btn-outline-secondary btn-sm"
                onClick={() => {
                  setEditingId(null);
                  setFormData(emptyForm);
                }}
              >
                Cancelar
              </button>
            )}
          </div>
        </form>
      )}

      {loading ? (
        <p>Cargando catalogo...</p>
      ) : (
        <table className="table table-striped">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Descripcion</th>
              <th>Precio</th>
              <th>Stock</th>
              {(isAdmin || canManageStock) && <th>Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.id}>
                <td>{p.name}</td>
                <td>{p.description}</td>
                <td>${p.price.toLocaleString('es-CL')}</td>
                <td>
                  {canManageStock ? (
                    <input
                      type="number"
                      className="form-control form-control-sm"
                      style={{ width: '90px' }}
                      defaultValue={p.stock}
                      onBlur={(e) => {
                        const newStock = Number(e.target.value);
                        if (newStock !== p.stock) handleStockChange(p.id!, newStock);
                      }}
                    />
                  ) : (
                    p.stock
                  )}
                </td>
                {(isAdmin || canManageStock) && (
                  <td>
                    {isAdmin && (
                      <>
                        <button
                          className="btn btn-sm btn-outline-primary me-1"
                          onClick={() => handleEdit(p)}
                        >
                          Editar
                        </button>
                        <button
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => handleDelete(p.id!)}
                        >
                          Eliminar
                        </button>
                      </>
                    )}
                  </td>
                )}
              </tr>
            ))}
            {!products.length && (
              <tr>
                <td colSpan={5} className="text-center text-muted">
                  No hay productos en el catalogo.
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
