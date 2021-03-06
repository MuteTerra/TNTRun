/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * 
 */

package tntrun.arena;

import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import tntrun.TNTRun;
import tntrun.bars.Bars;
import tntrun.messages.Messages;

public class GameHandler {

	private TNTRun plugin;
	private Arena arena;
	public GameHandler(TNTRun plugin, Arena arena)
	{
		this.plugin = plugin;
		this.arena = arena;
		count = arena.getCountdown();
	}
	
	//arena leave handler
	private int leavetaskid;
	public void startArenaAntiLeaveHandler()
	{
		leavetaskid = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable()
		{
			public void run()
			{
				for (Player player : Bukkit.getOnlinePlayers())
				{
					if (plugin.pdata.getArenaPlayers(arena).contains(player.getName()))
					{
						if (!arena.isInArenaBounds(player.getLocation()))
						{
							arena.arenaph.leavePlayer(player, Messages.playerlefttoplayer, Messages.playerlefttoothers);
						}
					}
				}
			}
		},0,1);
	}
	public void stopArenaAntiLeaveHandler()
	{
		Bukkit.getScheduler().cancelTask(leavetaskid);
	}
	
	
	//arena start handler (running status updater)
	int runtaskid;
	int count;
	public void runArenaCountdown()
	{
		arena.setStarting(true);
		runtaskid = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() 
		{
			public void run()
			{
				//check if countdown should be stopped for some various reasons
				HashSet<String> arenaplayers = plugin.pdata.getArenaPlayers(arena);
				if (!arena.isArenaEnabled())
				{
					stopArenaCountdown();
				} else
				if (arenaplayers.size() < arena.getMinPlayers())
				{
					for (Player player : Bukkit.getOnlinePlayers())
					{
						if (arenaplayers.contains(player.getName()))
						{
							Bars.setBar(player, Bars.waiting, arenaplayers.size(), 0, arenaplayers.size()*100/arena.getMinPlayers());
						}
					}
					plugin.signEditor.modifySigns(arena.getArenaName());
					stopArenaCountdown();
				} else
				//start arena if countdown is 0
				if (count == 0)
				{
					stopArenaCountdown();
					startArena();
				} else
				//countdown
				{
					for (Player player : Bukkit.getOnlinePlayers())
					{
						if (arenaplayers.contains(player.getName()))
						{
							Messages.sendMessage(player, Messages.arenacountdown, count);
							Bars.setBar(player, Bars.starting, 0, count, count*100/arena.getCountdown());
						}
					}
					count--;
				}
			}
		}, 0, 20);
	}
	public void stopArenaCountdown()
	{
		arena.setStarting(false);
		count = arena.getCountdown();
		Bukkit.getScheduler().cancelTask(runtaskid);
	}
	
	//main arena handler
	private int timelimit;
	private int arenahandler;
	public void startArena()
	{
		arena.setRunning(true);
		for (Player player : Bukkit.getOnlinePlayers())
		{
			if (plugin.pdata.getArenaPlayers(arena).contains(player.getName()))
			{
				Messages.sendMessage(player, Messages.arenastarted, arena.getTimeLimit());
			}
		}
		plugin.signEditor.modifySigns(arena.getArenaName());
		timelimit = arena.getTimeLimit()*20; //timelimit is in ticks
		arenahandler = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable()
		{
			public void run()
			{
				if (timelimit < 0)
				{
					for (Player player : Bukkit.getOnlinePlayers())
					{
						if (plugin.pdata.getArenaPlayers(arena).contains(player.getName()))
						{
							//kick player
							arena.arenaph.leavePlayer(player, Messages.arenatimeout, "");
						}
					}
					//stop arena
					stopArena();
					return;
				}
				HashSet<String> arenaplayers = plugin.pdata.getArenaPlayers(arena);
				//stop arena if player count is 0 (just in case)
				if (arenaplayers.size() == 0)
				{
					//stop arena
					stopArena();
					return;
				}
				//handle players
				for (Player player : Bukkit.getOnlinePlayers())
				{
					if (arenaplayers.contains(player.getName()))
					{
						//update bar
						Bars.setBar(player, Bars.playing, arenaplayers.size(), timelimit/20, timelimit*5/arena.getTimeLimit());
						//handle player
						handlePlayer(player);
					}
				}
				//decrease timelimit
				timelimit--;
			}
		}, 0, 1);
	}
	public void stopArena()
	{
		arena.setRunning(false);
		Bukkit.getScheduler().cancelTask(arenahandler);
		plugin.signEditor.modifySigns(arena.getArenaName());
		if (arena.isArenaEnabled())
		{
			startArenaRegen();
		}
	}

	//player handlers
	public void handlePlayer(final Player player)
	{
		Location plloc = player.getLocation();
		Location plufloc = plloc.clone().add(0,-1,0);
		//check for game location
		for (final GameLevel gl : arena.getGameLevels())
		{
			//remove block under player feet
			if (gl.isSandLocation(plufloc))
			{
				gl.destroyBlock(plufloc, arena);
			}
		}
		//check for win
		if (plugin.pdata.getArenaPlayers(arena).size() == 1)
		{
			//last player won
			arena.arenaph.leaveWinner(player, Messages.playerwontoplayer);
			broadcastWin(player);
			stopArena();
			return;
		}
		//check for lose
		if (arena.getLoseLevel().isLooseLocation(plloc))
		{
			//player lost
			arena.arenaph.leavePlayer(player, Messages.playerlosttoplayer, Messages.playerlosttoothers);
			return;
		}
	}
	private void broadcastWin(Player player)
	{
		Messages.broadsactMessage(player.getName(), arena.getArenaName(), Messages.playerwonbroadcast);
	}
	
	
	private void startArenaRegen()
	{
		//set arena is regenerating status
		arena.setRegenerating(true);
		//modify signs
		plugin.signEditor.modifySigns(arena.getArenaName());
		//start arena regen
		Thread regen = new Thread()
		{
			public void run()
			{
				try 
				{
					//regen
					for (final GameLevel gl : arena.getGameLevels())
					{
						if (!arena.isArenaEnabled()) {break;}
						int regentask = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
						{
							public void run()
							{
								if (arena.isArenaEnabled())
								{
									gl.regen();
								}
							}
						});
						while (Bukkit.getScheduler().isCurrentlyRunning(regentask) || Bukkit.getScheduler().isQueued(regentask))
						{
							Thread.sleep(10);
						}
						Thread.sleep(100);
					}
					Thread.sleep(100);
					if (!arena.isArenaEnabled()) {return;}
					//update arena status
					Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
					{
						public void run()
						{
							//set not regenerating status
							arena.setRegenerating(false);
							//modify signs
							plugin.signEditor.modifySigns(arena.getArenaName());
						}
					});
				} catch (Exception e) {}
			}
		};
		regen.start();
	}
	
}
