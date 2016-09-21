package br.com.algartelecom.meuPrimeiroProjeto;

import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import gov.nist.javax.sip.header.ims.PPreferredIdentityHeader;

import java.text.ParseException;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.TransactionState;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.CreateException;
import javax.slee.RolledBackContext;
import javax.slee.Sbb;
import javax.slee.SbbContext;
import javax.slee.facilities.TimerFacility;
import javax.slee.nullactivity.NullActivityContextInterfaceFactory;
import javax.slee.nullactivity.NullActivityFactory;
import javax.slee.resource.ResourceAdaptorTypeID;

import net.java.slee.resource.sip.CancelRequestEvent;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;
import utils.XMSRest;

import org.apache.log4j.Logger;
import org.mobicents.slee.SbbContextExt;
import org.mobicents.slee.SbbLocalObjectExt;

/**
 * Classe que implementa o SBB
 * 
 * @author caiocf
 *
 */
public abstract class CommonSbb implements Sbb {

	// Variavei declaradas no cmp-field do xml do SBB
	public abstract String getNumberB();

	public abstract void setNumberB(String numberB);

	public abstract String getNumberA();

	public abstract void setNumberA(String numberA);

	public abstract ActivityContextInterface getDialogA();

	public abstract void setDialogA(ActivityContextInterface aci);

	public abstract ActivityContextInterface getDialogB();

	public abstract void setDialogB(ActivityContextInterface aci);

	public abstract void setPAsserted(PAssertedIdentityHeader pAsserted);

	public abstract PAssertedIdentityHeader getPAsserted();

	public abstract void setPPreferred(PPreferredIdentityHeader pPreferred);

	public abstract PPreferredIdentityHeader getPPreferred();
	
	public abstract String getHrefA();

	public abstract void setHrefA(String hrefA);
	
	public abstract String getSdpA();

	public abstract void setSdpA(String sdpA);
	
	public abstract String getHrefB();

	public abstract void setHrefB(String hrefB);
	
	public abstract String getSdpB();

	public abstract void setSdpB(String sdpB);
	// fim

	public final static String JBOSS_BIND_ADDRESS = System.getProperty("jboss.bind.address", "127.0.0.1");
	private static final ResourceAdaptorTypeID sipRATypeID = new ResourceAdaptorTypeID("JAIN SIP", "javax.sip", "1.2");
	private static final String sipRALink = "SipRA";
	
	static XMSRest xmsrest;

	private static final Logger logger = Logger.getLogger(CommonSbb.class);

	private SbbContextExt sbbContext;

	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private HeaderFactory headerFactory;
	private SleeSipProvider sleeSipProvider;
	private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	private NullActivityContextInterfaceFactory nullAciFactoryApp;
	private NullActivityFactory nullActivityFactoryApp;
	private TimerFacility timerFacility;

	/**
	 * Metodo que inicial as variaveis necessaria ao receber a requisicao de
	 * INVITE
	 */
	public void setSbbContext(SbbContext context) {
		sbbContext = (SbbContextExt) context;

		try {
			// jain slee 1.0
			/*
			 * Context myEnv = (Context) new
			 * InitialContext().lookup("java:comp/env");
			 * sipActivityContextInterfaceFactory =
			 * (SipActivityContextInterfaceFactory)
			 * myEnv.lookup("slee/resources/jainsip/1.2/acifactory");
			 * sleeSipProvider = (SleeSipProvider)
			 * myEnv.lookup("slee/resources/jainsip/1.2/provider");
			 * messageFactory = sleeSipProvider.getMessageFactory();
			 * headerFactory = sleeSipProvider.getHeaderFactory();
			 * nullAciFactoryApp =
			 * (NullActivityContextInterfaceFactory)myEnv.lookup(
			 * NullActivityContextInterfaceFactory.JNDI_NAME);
			 * nullActivityFactoryApp =
			 * (NullActivityFactory)myEnv.lookup(NullActivityFactory.JNDI_NAME);
			 * timerFacility = (TimerFacility)
			 * myEnv.lookup(TimerFacility.JNDI_NAME);
			 */

			// jain slee 1.1
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) sbbContext
					.getActivityContextInterfaceFactory(sipRATypeID);
			sleeSipProvider = (SleeSipProvider) (SleeSipProvider) sbbContext.getResourceAdaptorInterface(sipRATypeID,
					sipRALink);
			nullAciFactoryApp = sbbContext.getNullActivityContextInterfaceFactory();
			addressFactory = sleeSipProvider.getAddressFactory();
			headerFactory = sleeSipProvider.getHeaderFactory();
			messageFactory = sleeSipProvider.getMessageFactory();

			nullActivityFactoryApp = sbbContext.getNullActivityFactory();
			timerFacility = sbbContext.getTimerFacility();
			
			xmsrest = new XMSRest("10.13.70.241", 81, this);

		} catch (Exception e) {
			logger.error("ERRO AO INICIAR O SBB CONTEXT:", e);
		}
	}

	// gets and sets

	public AddressFactory getAddressFactory() {
		return addressFactory;
	}

	public void setAddressFactory(AddressFactory addressFactory) {
		this.addressFactory = addressFactory;
	}

	public void setSipActivityContextInterfaceFactory(
			SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory) {
		this.sipActivityContextInterfaceFactory = sipActivityContextInterfaceFactory;
	}

	public void setHeaderFactory(HeaderFactory headerFactory) {
		this.headerFactory = headerFactory;
	}

	public void setMessageFactory(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	// end gets and sets

	/**
	 * Metodo que devolve 180 para ponta A
	 * 
	 * @param event
	 * @param sdp
	 */
	
	public void send180(byte[] sdp, String contentType) throws Exception {
		if (((DialogActivity) getDialogA().getActivity()).getState() == DialogState.TERMINATED) {
			return;
		}
		ActivityContextInterface serverTxACI = getServerTransactionACI();
		ServerTransaction st = (ServerTransaction) serverTxACI.getActivity();
		Response ringing = getMessageFactory().createResponse(Response.RINGING, st.getRequest());
		if (sdp != null) {
			ringing.setContentLength(headerFactory.createContentLengthHeader(sdp.length));
			String type[] = contentType.split("/");
			ringing.setContent(sdp, headerFactory.createContentTypeHeader(type[0], type[1]));
		}
		st.sendResponse(ringing);
	}

	/**
	 * Metodo que devolve 183 para ponta A
	 * 
	 * @param event
	 * @param sdp
	 */
	public void send183(String sdp, String contentType) throws Exception {
		if (((DialogActivity) getDialogA().getActivity()).getState() == DialogState.TERMINATED) {
			return;
		}
		ActivityContextInterface serverTxACI = getServerTransactionACI();
		ServerTransaction st = (ServerTransaction) serverTxACI.getActivity();
		Response response = getMessageFactory().createResponse(Response.SESSION_PROGRESS, st.getRequest());
		if (sdp != null) {
			response.setContentLength(headerFactory.createContentLengthHeader(sdp.length()));
			String type[] = contentType.split("/");
			response.setContent(sdp, headerFactory.createContentTypeHeader(type[0], type[1]));
		}
		st.sendResponse(response);
	}

	/**
	 * Metodo que devolve 200 para ponta A
	 * 
	 * @param event
	 * @param sdp
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	public void send200Answer(String sdp, String contentType) throws Exception {
		if (((DialogActivity) getDialogA().getActivity()).getState() == DialogState.TERMINATED) {
			return;
		}
		ActivityContextInterface serverTxACI = getServerTransactionACI();
		ServerTransaction st = (ServerTransaction) serverTxACI.getActivity();
		Response ok = getMessageFactory().createResponse(Response.OK, st.getRequest());
		ok.addHeader(createContactHeader(getNumberB()));
		((ToHeader) ok.getHeader(ToHeader.NAME)).setTag(st.getDialog().getLocalTag());
		if (sdp != null) {
			ok.setContentLength(headerFactory.createContentLengthHeader(sdp.length()));
			String type[] = contentType.split("/");
			ok.setContent(sdp, headerFactory.createContentTypeHeader(type[0], type[1]));
		}
		st.sendResponse(ok);
	}

	public void sendBye() throws Exception {
		if (((DialogActivity) getDialogA().getActivity()).getState() == DialogState.TERMINATED) {
			return;
		}
		final DialogActivity peerDialog = (DialogActivity) getDialogA().getActivity();

		peerDialog.sendRequest(peerDialog.createRequest(Request.BYE));

	}

	/**
	 * Metodo que retorna respostas para requisicoes SIP
	 * 
	 * @param event
	 * @param status
	 */
	protected void replyToRequestEvent(RequestEvent event, int status) {
		try {
			event.getServerTransaction()
					.sendResponse(sleeSipProvider.getMessageFactory().createResponse(status, event.getRequest()));
		} catch (Throwable e) {
			logger.error(" >>>> Erro ao devolver resposta " + status + ". Descricao: " + e.getMessage());
		}
	}

	/**
	 * 
	 * Metodo que processa as respostas de CANCEL
	 * 
	 * @param event
	 * @param status
	 */
	protected void acceptCancel(CancelRequestEvent event, ActivityContextInterface aci) {
		try {
			DialogActivity dialog = null;
			if (getDialogA() != null) {
				dialog = (DialogActivity) getDialogA().getActivity();
			}
			if (dialog != null) {
				if (DialogState.TERMINATED.equals(dialog.getState())) {
					replyToRequestEvent(event, Response.OK);
				} else {
					this.sleeSipProvider.acceptCancel(event, false);
				}
			} else {
				replyToRequestEvent(event, Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST);
			}
		} catch (Exception e) {
			logger.error(" >>>> Erro ao processar Cancel. Descricao: ", e);
			logger.error(" >>>> CancelRequestEvent: " + event);
		}

	}

	/**
	 * Cria contatct para responstas SIP
	 * 
	 * @param number
	 * @return
	 */
	protected ContactHeader createContactHeader(String number) {
		String localAddress = getSleeSipProvider().getListeningPoints()[0].getIPAddress();
		int localPort = getSleeSipProvider().getListeningPoints()[0].getPort();
		Address fromAddress = null;
		try {
			fromAddress = addressFactory.createAddress("sip:" + number + "@" + localAddress + ":" + localPort);
		} catch (ParseException e) {
		}

		return getHeaderFactory().createContactHeader(fromAddress);
	}

	/**
	 * Retorno o Dialog da outra ponta
	 * 
	 * @param aci
	 * @return Dialog quando existe, null quando nao encontrado
	 */
	protected Dialog getDialogOtherChannel(ActivityContextInterface aci) {
		Dialog dialog = null;
		if (aci.equals(getDialogA())) {
			if (getDialogB() == null) {
				return null;
			}
			dialog = (Dialog) getDialogB().getActivity();
		}
		if (aci.equals(getDialogB())) {
			if (getDialogA() == null) {
				return null;
			}
			dialog = (Dialog) getDialogA().getActivity();
		}
		return dialog;
	}

	/**
	 * Retorno o DialogActivity da outra ponta
	 * 
	 * @param aci
	 * @return DialogActivity quando existe, null quando nao encontrado
	 */
	protected DialogActivity getOtherDialogActivity(ActivityContextInterface aci) {
		DialogActivity dialog = null;
		if (aci.equals(getDialogA())) {
			dialog = (DialogActivity) getDialogB().getActivity();
		}
		if (aci.equals(getDialogB())) {
			dialog = (DialogActivity) getDialogA().getActivity();
		}
		return dialog;
	}

	/**
	 * Envia not-found para requisicao SIP
	 * 
	 * @param evt
	 */
	protected void sendNotFound(RequestEvent evt) {
		Request request = evt.getRequest();
		ServerTransaction tx = evt.getServerTransaction();
		try {
			Response response = getMessageFactory().createResponse(Response.NOT_FOUND, request);
			tx.sendResponse(response);
		} catch (Exception e) {
			logger.warn("Unexpected error: ", e);
		}
	}

	/**
	 * Utilizando ate antes do atendimento Retorno o ServerTransaction da
	 * ligacao
	 * 
	 * @return
	 */
	protected ActivityContextInterface getServerTransactionACI() {
		for (ActivityContextInterface aci : this.sbbContext.getActivities()) {
			if (aci.getActivity() instanceof ServerTransaction) {
				return aci;
			}
		}
		return null;
	}

	/**
	 * Realiza o detach de todos os activites do contexto
	 */
	
	protected void dettachAllActive() {
		for (ActivityContextInterface aci : this.getSbbContext().getActivities()) {
			try {
				aci.detach(getSbbLocalObject());
			} catch (Exception e) {
			}
		}
	}

	protected void finalizeTransactions() {
		for (ActivityContextInterface aci : this.getSbbContext().getActivities()) {
			if (aci.getActivity() instanceof ServerTransaction) {
				ServerTransaction st = (ServerTransaction) aci.getActivity();
				try {
					if (!TransactionState.TERMINATED.equals(st.getState())) {
						final Response response = getMessageFactory().createResponse(Response.REQUEST_TERMINATED,
								st.getRequest()); // 487
						st.sendResponse(response);
					} else {
						st.terminate();
					}
				} catch (Exception e) {
					logger.debug("Falha na finalizacao do server transaction");
				}
			} else if (aci.getActivity() instanceof ClientTransaction) {
				ClientTransaction ct = (ClientTransaction) aci.getActivity();
				try {
					ct.terminate();
				} catch (Exception e) {
					logger.debug("Falha na finalizacao do cliente transaction");
				}
			}
		}
	}

	protected String createSDPFake(String bind) {
		String sdp = "v=0\n"
                + "o=xmserver 1472562526 1472562527 IN IP4 192.168.56.101\n"
                + "s=xmserver\n"
                + "c=IN IP4 192.168.56.101\n"
                + "b=AS:80\n"
                + "t=0 0\n"
                + "m=audio 49152 RTP/AVP 9 0 8 96 97 4 18 98 99 100 3 105 104 103 101\n"
                + "b=AS:80\n"
                + "a=rtpmap:9 g722/8000\n"
                + "a=rtpmap:0 pcmu/8000\n"
                + "a=rtpmap:8 pcma/8000\n"
                + "a=rtpmap:96 g726-32/8000\n"
                + "a=rtpmap:97 amr/8000\n"
                + "a=fmtp:97 octet-align=0; mode-change-capability=2\n"
                + "a=rtpmap:4 g723/8000\n"
                + "a=fmtp:4 annexa=yes\n"
                + "a=rtpmap:18 g729/8000\n"
                + "a=fmtp:18 annexb=no\n"
                + "a=rtpmap:98 amr-wb/16000\n"
                + "a=fmtp:98 octet-align=0; mode-change-capability=2\n"
                + "a=rtpmap:99 iLBC/8000\n"
                + "a=fmtp:99 mode=30\n"
                + "a=rtpmap:100 opus/48000/2\n"
                + "a=rtpmap:3 gsm/8000\n"
                + "a=rtpmap:105 MP4A-LATM/16000\n"
                + "a=rtpmap:104 MPEG4-GENERIC/16000\n"
                + "a=rtpmap:103 gsm-efr/8000\n"
                + "a=rtpmap:101 telephone-event/8000\n"
                + "a=fmtp:101 0-15\n"
                + "a=ptime:20\n"
                + "a=maxptime:240\n"
                + "a=sendrecv\n"
                + "a=rtcp-mux";
		
		
		return sdp;
	}

	protected final NullActivityContextInterfaceFactory getNullAciFactoryApp() {
		return nullAciFactoryApp;
	}

	protected final NullActivityFactory getNullActivityFactoryApp() {
		return nullActivityFactoryApp;
	}

	protected final SipActivityContextInterfaceFactory getSipActivityContextInterfaceFactory() {
		return sipActivityContextInterfaceFactory;
	}

	protected final SbbContext getSbbContext() {
		return this.sbbContext;
	}

	public SleeSipProvider getSleeSipProvider() {
		return sleeSipProvider;
	}

	public void setSleeSipProvider(SleeSipProvider sleeSipProvider) {
		this.sleeSipProvider = sleeSipProvider;
	}

	public final SbbLocalObjectExt getSbbLocalObject() {
		return sbbContext.getSbbLocalObject();
	}

	protected final HeaderFactory getHeaderFactory() {
		return headerFactory;
	}

	public void unsetSbbContext() {
		this.sbbContext = null;
	}

	public void sbbActivate() {
	}

	public void sbbCreate() throws CreateException {

	}

	public void sbbExceptionThrown(Exception arg0, Object arg1, ActivityContextInterface arg2) {
	}

	public void sbbLoad() {
	}

	public void sbbPassivate() {
	}

	public void sbbPostCreate() throws CreateException {
	}

	public void sbbRemove() {
	}

	public void sbbRolledBack(RolledBackContext arg0) {
	}

	public void sbbStore() {
	}

	protected final MessageFactory getMessageFactory() {
		return messageFactory;
	}

	protected final TimerFacility getTimerFacility() {
		return timerFacility;
	}

	/**
	 * Salva o dialogo da ponta A e o atacha no SBB
	 * 
	 * @param event
	 */
	
	protected void attachToDialog(RequestEvent event) {
		try {
			Dialog dialog = getSleeSipProvider().getNewDialog(event.getServerTransaction());
			dialog.terminateOnBye(true);
			ActivityContextInterface dialogAciA = getSipActivityContextInterfaceFactory()
					.getActivityContextInterface((DialogActivity) dialog);
			dialogAciA.attach(getSbbLocalObject());
			setDialogA(dialogAciA);
		} catch (SipException e) {
			throw new RuntimeException("Error creating SIP Dialog", e);
		}
	}

}
