import { useRoles } from '../auth/useRoles';

export function DashboardPage() {
  const roles = useRoles();
  const isAdmin = roles.includes('ADMIN');
  const isOperador = roles.includes('OPERADOR');
  const isCliente = roles.includes('CLIENTE');

  return (
    <div className="mt-4">
      <h2>Dashboard</h2>

      {isAdmin && (
        <div className="alert alert-primary">
          <strong>Vista Admin:</strong> administras el catalogo de productos, el stock y tienes
          visibilidad de todos los pedidos del sistema.
        </div>
      )}

      {isOperador && (
        <div className="alert alert-info">
          <strong>Vista Operador:</strong> gestionas los pedidos del dia y cambias sus estados, y
          puedes administrar el stock del catalogo.
        </div>
      )}

      {isCliente && (
        <div className="alert alert-success">
          <strong>Vista Cliente:</strong> aqui puedes crear nuevos pedidos y hacer seguimiento a
          los tuyos.
        </div>
      )}

      {roles.length === 0 && (
        <div className="alert alert-warning">
          Tu usuario no tiene ningun grupo (rol) asignado en Cognito todavia. Pide a un
          administrador que te agregue al grupo ADMIN, OPERADOR o CLIENTE.
        </div>
      )}
    </div>
  );
}
