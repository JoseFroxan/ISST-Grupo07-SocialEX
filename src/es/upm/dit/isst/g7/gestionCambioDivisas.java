package es.upm.dit.isst.g7;

import java.io.IOException;

import javax.mail.Address; 
import javax.mail.Message; 
import javax.mail.MessagingException;
import javax.mail.Session; 
import javax.mail.Transport; 
import javax.mail.internet.InternetAddress; 
import javax.mail.internet.MimeMessage;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties; 

import es.upm.dit.isst.g7.dao.ClienteDAO;
import es.upm.dit.isst.g7.dao.ClienteDAOImpl;
import es.upm.dit.isst.g7.dao.CuentaDAO;
import es.upm.dit.isst.g7.dao.CuentaDAOImpl;
import es.upm.dit.isst.g7.dao.MatchingCambioDivisasDAO;
import es.upm.dit.isst.g7.dao.MatchingCambioDivisasDAOImpl;
import es.upm.dit.isst.g7.dao.SolicitudCambioDivisasDAO;
import es.upm.dit.isst.g7.dao.SolicitudCambioDivisasDAOImpl;
import es.upm.dit.isst.g7.dao.TransaccionDAO;
import es.upm.dit.isst.g7.dao.TransaccionDAOImpl;
import es.upm.dit.isst.model.Cuenta;
import es.upm.dit.isst.model.MatchingCambioDivisas;
import es.upm.dit.isst.model.SolicitudCambioDivisas;
import es.upm.dit.isst.model.Transaccion;
import es.upm.dit.isst.model.Transaccion.Tipo;
import es.upm.dit.isst.yahoo.YahooCurrencyConverter;

@SuppressWarnings("serial")
public class gestionCambioDivisas extends HttpServlet {
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

	}

	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		
		Long cuentaAcepta = Long.parseLong(req.getParameter("cuentaAcepta"));
		System.out.println("cuentaSolicitante: "+cuentaAcepta);
		
		Long cuentaSolicitante = Long.parseLong(req.getParameter("cuentaSolicitante"));
		System.out.println("cuentaSolicitante: "+cuentaSolicitante);
		
		
		Long idSolicitud = Long.parseLong(req.getParameter("idSolicitud"));
		System.out.println("idSolicitud: "+idSolicitud);
		
		String user = req.getUserPrincipal().getName();
		String action = req.getParameter("action");
		
		if(user!= null){
			
			SolicitudCambioDivisasDAO daoSolicitud = SolicitudCambioDivisasDAOImpl.getInstance();
			
			if(action.equals("Cambiar")){
				System.out.println("Cambiar");
				//Acepta solicitud
				SolicitudCambioDivisas solicitud = daoSolicitud.read(idSolicitud);
				//Datos de solicitud
				double importeMonedaOriginal = solicitud.getimporteDivisaOriginal();
				double importeMonedaCambiada = solicitud.getimporteDivisaACambiar();
				String divisaOriginal = solicitud.getDivisaPredeterminada();
				String divisaCambio = solicitud.getDivisaCambio();
				CuentaDAO daoCuenta = CuentaDAOImpl.getInstance();
				//Cuenta que solicita el cambio de divisas
				Cuenta cuentaMonedaOrigen = daoCuenta.GetCuenta(cuentaSolicitante);
				//Cuenta que acepta el cambio de divisas
				Cuenta cuentaMonedaACambiar = daoCuenta.GetCuenta(cuentaAcepta);
				//Comprueba que hay fondos en las cuenta para realizar el cambio
				if(cuentaMonedaOrigen.getSaldo(solicitud.getDivisaPredeterminada())>=importeMonedaOriginal
						&& cuentaMonedaACambiar.getSaldo(solicitud.getDivisaCambio())>=importeMonedaCambiada){
					System.out.println("Hay fondos");
					//Registra la operación en la tabla MatchingCambioDivisas
					MatchingCambioDivisasDAO daoMatching = MatchingCambioDivisasDAOImpl.getInstance();
					//Crea una solicitud para el cliente 2. Tarjeta es 0 porque el cambio se hace con el saldo de la cuenta.
					SolicitudCambioDivisas nuevaSol = daoSolicitud.Create(2, importeMonedaCambiada, divisaOriginal, divisaCambio, cuentaAcepta, 2, (long) 0, importeMonedaOriginal);
					MatchingCambioDivisas matching = daoMatching.Create(1, idSolicitud, nuevaSol.getId(), 0.0);
					//Actualiza el saldo de las cuentas de los 2 clientes
					//Cuenta 1
					cuentaMonedaOrigen.addSaldo(divisaOriginal, importeMonedaOriginal*(-1));
					cuentaMonedaOrigen.addSaldo(divisaCambio, importeMonedaCambiada);
					daoCuenta.update(cuentaMonedaOrigen);
					//Cuenta 2
					cuentaMonedaACambiar.addSaldo(divisaOriginal, importeMonedaOriginal);
					cuentaMonedaACambiar.addSaldo(divisaCambio, importeMonedaCambiada*(-1));
					daoCuenta.update(cuentaMonedaACambiar);

					//Registra la operación en la tabla Transacciones
					TransaccionDAO daoTransaccion = TransaccionDAOImpl.getInstance();
					//Fecha
					Calendar hoy = Calendar.getInstance();
					SimpleDateFormat format1 = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
					String formatted = format1.format(hoy.getTime());
					//
					Transaccion trancuentaMonedaOrigenAddSaldo = daoTransaccion.createTransaccion(cuentaSolicitante, formatted, divisaOriginal, importeMonedaOriginal*(-1), "Cambio de divisas", Tipo.CAMBIO_DIVISAS_SACAR, "Cuenta");
					Transaccion trancuentaMonedaOrigenQuitaSaldo = daoTransaccion.createTransaccion(cuentaSolicitante, formatted, divisaCambio, importeMonedaCambiada, "Cambio de divisas", Tipo.CAMBIO_DIVISAS_ADD, "Cuenta");
					daoTransaccion.update(trancuentaMonedaOrigenQuitaSaldo);
					daoTransaccion.update(trancuentaMonedaOrigenAddSaldo);
					//
					Transaccion trancuentaMonedaACambiarAddSaldo = daoTransaccion.createTransaccion(cuentaAcepta, formatted, divisaOriginal, importeMonedaOriginal, "Cambio de divisas", Tipo.CAMBIO_DIVISAS_ADD, "Cuenta");
					Transaccion trancuentaMonedaACambiarQuitaSaldo = daoTransaccion.createTransaccion(cuentaAcepta, formatted, divisaCambio, importeMonedaCambiada*(-1), "Cambio de divisas", Tipo.CAMBIO_DIVISAS_SACAR, "Cuenta");
					daoTransaccion.update(trancuentaMonedaACambiarAddSaldo);
					daoTransaccion.update(trancuentaMonedaACambiarQuitaSaldo);
					
					//Actualiza estado
					solicitud.setEstado(2);
					daoSolicitud.Update(solicitud);
				}else{
					System.out.println("No hay fondos");
					resp.sendRedirect("/isst_grupo07_socialex");
				}
				
			}
			//Elimina Solicitud de cambio
			if(action.equals("Cancelar")){
				System.out.println("Cancelar");
				SolicitudCambioDivisas solicitudEliminar = daoSolicitud.read(idSolicitud);
				daoSolicitud.Delete(solicitudEliminar);
				
			}
			
		}
		resp.sendRedirect("/isst_grupo07_socialex");
		}
}
