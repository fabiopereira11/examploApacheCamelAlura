package br.com.caelum.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import com.thoughtworks.xstream.XStream;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {	

		final XStream xStream = new XStream();
		xStream.alias("negociacao", Negociacao.class);
		
		CamelContext context = new DefaultCamelContext();
		context.addRoutes(new RouteBuilder() { //cuidado, não é RoutesBuilder

		    @Override
		    public void configure() throws Exception {
		    	
		    	
		       //deve ser configurado antes de qualquer rota
		       onException(Exception.class).
	    	    handled(true).
	    	        maximumRedeliveries(3).
	    	            redeliveryDelay(4000).
	    	        onRedelivery(new Processor() {

	    	            @Override
	    	            public void process(Exchange exchange) throws Exception {
	    	                    int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
	    	                    int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
	    	                    System.out.println("Redelivery - " + counter + "/" + max );;
	    	            }
	    	    });
		    	
		    	errorHandler(
		    		    deadLetterChannel("file:erro").
		    		        logExhaustedMessageHistory(true).
		    		        maximumRedeliveries(3).
		    		            redeliveryDelay(5000).
		    		        onRedelivery(new Processor() {            
		    		            @Override
		    		            public void process(Exchange exchange) throws Exception {
		    		                int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
		    		                int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
		    		                System.out.println("Redelivery - " + counter + "/" + max );
		    		            }
		    		        })
		    		);
		    	
//		    	 from("timer://negociacoes?fixedRate=true&delay=3s&period=360s")
//		         .to("http4://argentumws.caelum.com.br/negociacoes")
//		         .convertBodyTo(String.class)
//		         .unmarshal(new XStreamDataFormat(xStream))
//		         .split(body())
//		         .log("${body}")
//		       .end(); //só deixa explícito que é o fim da rota
		    	
		    	//timer para iniciar dentro de range de horarios
		    	from("timer://myTimer?repeatCount=1")
		    	.log("${date:now:HHmm}	")
		    	.choice()
		    		.when().simple("${date:now:HHmm} >= 0600 && ${date:now:HHmm} < 1800")
		    		.log("ok")
		    		.otherwise()
		            .log("Other order received")
		    	.end();
		    	
		    	from("file:pedidos?delay=5s&noop=true").		    		
			        routeId("rota-pedidos").
			        to("seda:soap").
			        to("seda:http");
	
			    from("seda:soap").
				    routeId("rota-soap").
				    to("xslt:pedido-para-soap.xslt").
				        log("Resultado do template: ${body}").
				        setHeader(Exchange.CONTENT_TYPE,constant("text/xml")).
			    to("http4://localhost:8080/webservices/financeiro");
	
			    from("seda:http").
			        routeId("rota-http").
			        setProperty("pedidoId", xpath("/pedido/id/text()")).
			        setProperty("email", xpath("/pedido/pagamento/email-titular/text()")).
			        split().
			            xpath("/pedido/itens/item").
			        filter().
			            xpath("/item/formato[text()='EBOOK']").
			        setProperty("ebookId", xpath("/item/livro/codigo/text()")).
			        setHeader(Exchange.HTTP_QUERY,
			                simple("clienteId=${property.email}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}")).
			    to("http4://localhost:8080/webservices/ebook/item");
			 }
		});
		
		

		context.start();
		Thread.sleep(20000);
		context.stop();

	}	
}
