package cmsc433.p1;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

/*
 * A bidder who only bids on items he or she can afford with respect to
 * the worst case assumption that he or she wins all outstanding bids.
 */
public class ConservativeBidder implements Bidder
{
	private String name;
	private int cash;
	private int cycles;
	private int maxSleepTimeMs;
	private int initialCash;

	private Random rand;

	private AuctionServer server;

	private int mostItemsAvailable = 0;

	public ConservativeBidder(AuctionServer server, String name, int cash, int cycles, int maxSleepTimeMs, long randomSeed)
	{
		this.name = name;
		this.cash = cash;
		this.cycles = cycles;
		this.maxSleepTimeMs = maxSleepTimeMs;
		this.initialCash = cash;

		this.rand = new Random(randomSeed);

		this.server = server;
	}

	@Override
	public int cash()
	{
		return this.cash;
	}

	@Override
	public int cashSpent()
	{
		return this.initialCash - this.cash;
	}

	@Override
	public int mostItemsAvailable()
	{
		return this.mostItemsAvailable;
	}

	@Override
	public String name()
	{
		return this.name;
	}

	@Override
	public void run()
	{
		List<Item> activeBids = new ArrayList<Item>();
		Hashtable<Item, Integer> activeBidPrices = new Hashtable<Item, Integer>();
		int sumActiveBids = 0;

		for (int i = 0; (i < cycles && cash > 0) || activeBids.size() > 0; ++i)
		{
			List<Item> items = server.getItems();
			if (items.size() > this.mostItemsAvailable) { this.mostItemsAvailable = items.size(); }

			while (items.size() > 0)
			{

				int index = rand.nextInt(items.size());

				Item item = items.get(index);
				items.remove(index);

				int price = server.itemPrice(item.listingID());
				if (price != -1 && price < this.cash  - sumActiveBids)
				{
					// The server should ensure thread safety: if the price
					// has already increased, then this bid should be invalid.
					boolean success = server.submitBid(item.listingID(), this.name(),  price + 1);

					if (success)
					{
						if (!activeBidPrices.containsKey(item))
						{
							activeBids.add(item);
						}
						else
						{
							sumActiveBids -= activeBidPrices.get(item);
						}
	
						sumActiveBids += price + 1;
						activeBidPrices.put(item, price + 1);
					}
					break;
				}

				continue;
			}

			List<Item> newActiveBids = new ArrayList<Item>();
			Hashtable<Item, Integer> newActiveBidPrices = new Hashtable<Item, Integer>();
			
			for (Item bid : activeBids)
			{
				//System.out.println("Before switch");
				switch (server.checkBidStatus(bid.listingID(), this.name()))
				{
				case 0:
					// Success
					int finalPrice = activeBidPrices.get(bid);
					int cashToPay = this.cash >= finalPrice ? finalPrice : this.cash;
					
					try {
						String itemWon = server.payForItem(bid.listingID(), this.name(), cashToPay);
					} catch (InsufficientFundsException e){
						System.out.println(this.name() + " was unable to pay up and is retiring in disgrace.");
						return;
					}
					this.cash -= cashToPay;
					sumActiveBids -= cashToPay;

					break;

				case 1:
					// Open
					newActiveBids.add(bid);
					newActiveBidPrices.put(bid, activeBidPrices.get(bid));
					break;

				case 2:
					// Failed
					sumActiveBids -= activeBidPrices.get(bid);
					break;

				default:
					// Error
					break;
				}
			}

			activeBids = newActiveBids;
			activeBidPrices = newActiveBidPrices;

			try
			{
				Thread.sleep((long)rand.nextInt(this.maxSleepTimeMs));
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}

}
