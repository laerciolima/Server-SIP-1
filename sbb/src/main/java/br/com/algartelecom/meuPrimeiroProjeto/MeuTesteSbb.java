package br.com.algartelecom.meuPrimeiroProjeto;

import java.util.Arrays;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sip.ClientTransaction;
import javax.sip.DialogState;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionState;
import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.facilities.TimerEvent;
import javax.slee.facilities.TimerOptions;
import javax.slee.facilities.TimerPreserveMissed;

import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.DialogTimeoutEvent;
import utils.XMSRest;

import org.apache.log4j.Logger;

import gov.nist.javax.sip.message.Content;

public abstract class MeuTesteSbb extends CommonSbb {

	private static final Logger logger = Logger.getLogger(MeuTesteSbb.class);
	private static TimerOptions timerOptions;

	private TimerOptions getTimerOptions() {
		if (timerOptions == null) {
			timerOptions = new TimerOptions();
			timerOptions.setPreserveMissed(TimerPreserveMissed.ALL);
		}
		return timerOptions;
	}

	/**
	 * Recebe pedido de INVITE da NGN e inicia a aplicação URA ou CS
	 * 
	 * @param event
	 * @param aci
	 */
	public void onInvite(RequestEvent event, ActivityContextInterface aci) {

		try {
			aci.attach(getSbbLocalObject());
			attachToDialog(event);

			SipURI sipRequest = (SipURI) event.getRequest().getRequestURI();
			setNumberB(sipRequest.getUser()); // Salva numero de B na Sessao

			FromHeader fromHeader = (FromHeader) event.getRequest().getHeader(FromHeader.NAME);
			SipURI fromSipURI = (SipURI) fromHeader.getAddress().getURI();
			String from = fromSipURI.toString();
			setNumberA(fromSipURI.getUser()); // Salva numero de A na Sessao

			replyToRequestEvent(event, Response.TRYING);
			replyToRequestEvent(event, Response.RINGING); // Retorno 100

			System.out.println("inicio###############################################");
			String sdp = new String(event.getRequest().getRawContent());

			sdp = sdp.replaceAll("\r\n", "&#xD;&#xA;");

			String req = "<web_service version=\"1.0\"><call sdp=\"" + sdp
					+ "\" media=\"audio\" signaling=\"no\"/></web_service>";

			System.out.println("+++++++++" + req + "++++++++");
			String response = xmsrest.sendRequestPOST("/default/calls", req);

			setHrefA(xmsrest.getHrefFromResponse(response));

			req = "<web_service version=\"1.0\"><call sdp=\"\" media=\"audio\" signaling=\"no\"/></web_service>";

			System.out.println("+++++++++" + req + "++++++++");
			response = xmsrest.sendRequestPOST("/default/calls", req);

			setHrefB(xmsrest.getHrefFromResponse(response));
			setSdpB(xmsrest.getSdpFromResponse(response).replaceAll("&#xD;&#xA;", "\r\n"));

			System.out.println("fim###############################################");

			getTimerFacility().setTimer(aci, null, System.currentTimeMillis() + 5000, getTimerOptions());

		} catch (Exception e) {
			logger.error("Erro ao completar ao atender a chamada: ", e);
			replyToRequestEvent(event, Response.NOT_FOUND);
			dettachAllActive();
		}

	}

	/**
	 * Recebe pedido de REINVITE da NGN e repassa para URA
	 * 
	 * @param event
	 * @param aci
	 */
	public void onReInvite(RequestEvent event, ActivityContextInterface aci) {

	}

	/**
	 * Recebe PRACK da NGN e Devolve 200, Deve ser desenvolvido posteriormente
	 * 
	 * @param event
	 * @param aci
	 */
	public void onPrackRequest(RequestEvent event, ActivityContextInterface aci) {

	}

	/**
	 * Recebe PRACK de um dialogo ja estabelecido e Devolve 200,
	 * 
	 * @param event
	 * @param aci
	 */
	public void onPrackDialog(RequestEvent event, ActivityContextInterface aci) {
	}

	/**
	 * Recebe ACK
	 * 
	 * @param event
	 * @param aci
	 */
	public void onAck(RequestEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando ACK <<<<");
		System.out.println("################ chegou ACK");

		String req = "<web_service version=\"1.0\">" + " <call>" + " <call_action>"
				+ " <play offset=\"0s\" repeat=\"0\" delay=\"1s\" terminate_digits=\"#\" skip_interval=\"1s\">"
				+ " <play_source audio_uri=\"file://verification/play_menu.wav\" audio_type=\"audio/x-wav\" />"
				+ " </play>" + " </call_action>" + "</call>" + "</web_service>";

		xmsrest.sendRequestPUT(getHrefA(), req);
		
		xmsrest.sendRequestPUT(getHrefB(), req);

		req = "	<web_service version=\"1.0\">";
		req += " <call>";
		req += " <call_action>";
		req += " <playrecord recording_audio_uri=\"file://recorded_file.wav\" ";
		req += "recording_audio_type=\"audio/x-wav\"";
		req += " max_time=\"10s\" offset=\"0s\" repeat=\"0\" delay=\"1s\"";
		req += "terminate_digits=\"#\"";
		req += " beep=\"yes\" barge=\"yes\" cleardigits=\"yes\" >";
		req += " <play_source audio_uri=\"file://verification/play_menu.wav\"";
		req += "audio_type=\"audio/x-wav\"/>";
		req += " </playrecord>";
		req += " </call_action>";
		req += "</call>";
		req += "</web_service>";

		

		

	}

	/**
	 * Recebe CANCEL
	 * 
	 * @param event
	 * @param aci
	 */
	public void onCancel(CancelRequestEvent event, ActivityContextInterface aci) {

		logger.debug(">>>> Chegando CANCEL <<<<");
		try {
			DialogActivity dialog = (DialogActivity) getDialogA().getActivity();
			if (dialog != null) {
				getSleeSipProvider().acceptCancel(event, false);
			} else {
				Request request = event.getRequest();
				ServerTransaction tx = (ServerTransaction) event.getServerTransaction();
				Response response = getMessageFactory().createResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST,
						request);
				tx.sendResponse(response);
			}
		} catch (Exception e) {
			logger.error(" >>>> Erro ao processar Cancel. Descricao: " + e);
		}
	}

	/**
	 * Recebe os pedidos de BYE
	 * 
	 * @param event
	 * @param aci
	 */
	public void onBye(RequestEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando BYE <<<<");
		replyToRequestEvent(event, Response.OK);
		xmsrest.sendRequestDELETE(getHrefA());
		xmsrest.sendRequestDELETE(getHrefB());
		finalizeTransactions();
	}

	/**
	 * Recebe as respostas SIP de Provisionamento 1XX
	 * 
	 * @param event
	 * @param aci
	 */
	public void on1xxCall(ResponseEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando 1XX <<<<");
	}

	/**
	 * Recebe as respostas SIP de confirmacao 2XX
	 * 
	 * @param event
	 * @param aci
	 */
	public void on2xxCall(ResponseEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando 2XX <<<<");

	}

	/**
	 * Metodo que recebe os Erros de cliente das requisicoes SIP 4XX
	 * 
	 * @param event
	 * @param aci
	 */
	public void on4xxCall(ResponseEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando 4XX <<<<");
	}

	/**
	 * Metodo que recebe os Erros de cliente das requisicoes SIP 5XX
	 * 
	 * @param event
	 * @param aci
	 */
	public void on5xxCall(ResponseEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando 5XX <<<<");
	}

	/**
	 * Metodo que recebe os Erros de cliente das requisicoes SIP 6XX
	 * 
	 * @param event
	 * @param aci
	 */
	public void on6xxCall(ResponseEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando 6XX <<<<");
	}

	/**
	 * Metodo que recebe os INFO da rede e devolve 200
	 * 
	 * @param event
	 * @param aci
	 */
	public void onInfoDialog(RequestEvent event, ActivityContextInterface aci) {
		logger.debug(">>>> Chegando INFO - devolvendo 200 <<<<");
		replyToRequestEvent(event, Response.OK);
	}

	/**
	 * Metodo que faz o atendimento do dialogo da ponta A com o telefone
	 * 
	 * @param event
	 * @param aci
	 */
	public void onTimerEvent(TimerEvent event, ActivityContextInterface aci) {

		// send 200 ok

		System.out.println("--------------------###----------------"
				+ ((DialogActivity) getDialogA().getActivity()).getState().toString());
		System.out.println(
				"--------------------@@@----------------" + ((DialogActivity) getDialogA().getActivity()).getState());

		try {
			// aci.detach(getSbbLocalObject());

			if (((DialogActivity) getDialogA().getActivity()).getState() == DialogState.CONFIRMED) {
				System.out.println("Enviando by para o softphone...");
				sendBye();
				return;
			}

			String bind = getSleeSipProvider().getListeningPoints()[0].getIPAddress();
			String sdp = getSdpB();
			System.out.println("-------------------------------------SDP\n" + sdp);
			System.out.println("\n FIMSDP -------------------------------------\n");
			logger.info("Atendendo a chamada depois de 10 segundos");
			send200Answer(sdp, "application/sdp");

		} catch (Exception e) {
			logger.error("Erro atender a chamada: ", e);
			dettachAllActive();
		}

	}

	/**
	 * Metodo que controla os TIME-OUT das requisicoes SIP
	 * 
	 * @param event
	 * @param aci
	 */
	public void onTransactionTimeoutEvent(TimeoutEvent event, ActivityContextInterface aci) {

		logger.debug(">>>>>>>>>>>>>>>TIME OUT<<<<<<<<<<<<<");
		ClientTransaction clientTransaction = event.getClientTransaction();
		if (clientTransaction != null) {
			try {
				clientTransaction.getDialog().delete();
				aci.detach(getSbbLocalObject()); // Para de escutar os eventos
													// desta transacao
			} catch (Exception e) {
				logger.error("Erro ao finalizar ligacao");
			}

		} else {
			try {
				if (event.isServerTransaction()) {
					ServerTransaction st = event.getServerTransaction();
					if (st != null && !st.getState().equals(TransactionState.TERMINATED)) {
						final Response response = getMessageFactory().createResponse(Response.REQUEST_TIMEOUT,
								st.getRequest());
						st.sendResponse(response);
					}
				}
				aci.detach(getSbbLocalObject()); // Para de escutar os eventos
													// desta transacao
			} catch (Exception e) {
				logger.error("Erro ao finalizar ligacao");
			}
		}

	}

	public void onTimeoutDialog(DialogTimeoutEvent event, ActivityContextInterface aci) {
		logger.error("RECEBI UM DIALOGO TIME OUT.");
		logger.error("DialogTimeoutEvent: " + event);
		logger.error("ActivityContextInterface: " + aci);
	}

}
