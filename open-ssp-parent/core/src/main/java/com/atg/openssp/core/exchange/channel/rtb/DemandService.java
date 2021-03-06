package com.atg.openssp.core.exchange.channel.rtb;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atg.openssp.common.cache.CurrencyCache;
import com.atg.openssp.common.core.entry.SessionAgent;
import com.atg.openssp.common.demand.BidExchange;
import com.atg.openssp.common.demand.ResponseContainer;
import com.atg.openssp.common.exception.InvalidBidException;
import com.atg.openssp.common.provider.AdProviderReader;
import com.atg.openssp.core.cache.type.SupplierCache;
import com.atg.openssp.core.exchange.Auction;
import com.atg.openssp.core.exchange.BidRequestBuilder;

import openrtb.bidrequest.model.BidRequest;
import openrtb.bidrequest.model.Impression;

/**
 * @author André Schmer
 *
 */
public class DemandService implements Callable<AdProviderReader> {

	private static final Logger log = LoggerFactory.getLogger(DemandService.class);

	private final SessionAgent agent;

	/**
	 * 
	 * @param {@link
	 *            SessionAgent}
	 */
	public DemandService(final SessionAgent agent) {
		this.agent = agent;
	}

	/**
	 * Calls the DSP. Collects the results of bidrequest, storing the results after validating into a {@link BidExchange} object.
	 * 
	 * <p>
	 * Principle of work is the following:
	 * <ul>
	 * <li>Loads the connectors as callables from the cache {@link DemandBroker}</li>
	 * <li>Invoke the callables due to the {@link DemandExecutorServiceFacade}</li>
	 * <li>For every result in the list of futures, the response will be validated {@link OpenRtbVideoValidator} and stored in a {@link BidExchange} object</li>
	 * <li>From the set of reponses in the {@link BidExchange} a bidding winner will be calculated in the Auction service {@link Auction}</li>
	 * </ul>
	 * <p>
	 * 
	 * @return {@link AdProviderReader}
	 * @throws Exception
	 */
	@Override
	public AdProviderReader call() throws Exception {
		AdProviderReader adProvider = null;
		try {
			final List<DemandBroker> connectors = loadSupplierConnectors();
			final List<Future<ResponseContainer>> futures = DemandExecutorServiceFacade.instance.invokeAll(connectors);

			futures.parallelStream().filter(f -> f != null).forEach(future -> {
				try {
					final ResponseContainer responseContainer = future.get();
					// final boolean valid = OpenRtbVideoValidator.instance.validate(agent.getBidExchange().getBidRequest(responseContainer.getSupplier()),
					// responseContainer
					// .getBidResponse());
					//
					// if (false == valid) {
					// LogFacade.logException(this.getClass(), ExceptionCode.E003, agent.getRequestid(), responseContainer.getBidResponse().toString());
					// return;// important!
					// }
					agent.getBidExchange().setBidResponse(responseContainer.getSupplier(), responseContainer.getBidResponse());
				} catch (final ExecutionException | InterruptedException e) {
					log.error("{} {}", agent.getRequestid(), e.getMessage());
				}
			});

			try {
				adProvider = Auction.auctioneer(agent.getBidExchange());
			} catch (final InvalidBidException e) {
				log.error("{} {}", agent.getRequestid(), e.getMessage());
			}
		} catch (final InterruptedException e) {
			log.error(" InterruptedException (outer) {} {}", agent.getRequestid(), e.getMessage());
		}

		return adProvider;
	}

	/**
	 * Loads the connectors for supplier from the cache.
	 * <p>
	 * Therefore it prepares the {@link BidRequest} for every connector, which is a representant to a demand connection.
	 * 
	 * @return a {@code List} with {@link DemandBroker}
	 * 
	 * @link SessionAgent
	 */
	private List<DemandBroker> loadSupplierConnectors() {
		final List<DemandBroker> brokerList = SupplierCache.instance.getAll();
		final BidRequest bidRequest = BidRequestBuilder.build(agent);
		brokerList.forEach(broker -> {
			final Impression imp = bidRequest.getImp().get(0);
			if (agent.getParamValues().getVideoad().getBidfloorPrice() > 0) {
				// floorprice in EUR -> multiply with rate to get target
				// currency therfore floorprice currency is always the same
				// as supplier currency
				imp.setBidfloor(agent.getParamValues().getVideoad().getBidfloorPrice() * CurrencyCache.instance.get(broker.getSupplier().getCurrency()));
				imp.setBidfloorcur(broker.getSupplier().getCurrency());
			}

			broker.setSessionAgent(agent);
			agent.getBidExchange().setBidRequest(broker.getSupplier(), bidRequest);
		});

		return brokerList;
	}

}
