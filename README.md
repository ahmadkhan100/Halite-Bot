# Halite-Bot

I created this bot as a part of SIG, online trading feat.
===========================
General outline of strategy
===========================


My bot has two modes -- expansion and normal mode. Normal mode was initially inspired from looking at Adereth's bot when the competition first started. At some point I realized that Ben Spector's (Sydriax) "tunelling" was much more efficient in maps with high strength/production ratio. So I added an 'expansion mode' to my bot.

Expansion mode consists of calculating a score for each neutral adjacent tile and enlisting strength to take the best tiles. 
The score was basically production / strength, but I take into account production loss and also looked 2 steps ahead. It worked out pretty well because looking 2 steps ahead was often good enough due to the smoothness of the maps.
Then the bot tries to take the best tile by doing a BFS inwards to find enough strength (and future strength). If there's still strength left, it moves on to the next best tile, etc.
This heuristic is still buggy since I can see useless moves as pieces sometimes move back and forth changing their mind on which tile to go for.
Expansion mode stops as soon as the bot sees an enemy or if the neutral territory has a low strength / production score (i.e. easy to take).

Normal mode (or battle mode) is a lot of small heuristics. Here is the list:
- Move inner (non combat) pieces towards the closest border only looking at 4 directions (no diagonals). It was good enough.
- Be more aggressive towards enemy/neutral pieces with high strength / production.
- In general, don't move unless a piece's strength is 6x its production
- Take neutral piece with most production / strength. Prefer fighting other players over taking neutral pieces.
- If a piece is in combat with an enemy, enlist 255 strength worth of closest friendly pieces to go fight. This was crucial in countering Dan Shields' amazing performance in small maps when fighting started very early.
- Maximize splash damage. I tried to do some simulated annealing to pre-empty enemy moves, but failed. In the end I just looked at the sum of potential damage inflicted.
- The 'repulsion map' helped to space out large pieces so that overkill would be slightly less likely. In practice, it doesn't seem to have worked as well as sending pieces one by one into combat like top players have done in the open competition. Dan Shield sent pieces one by one with his final bot, which made his 1v1 much stronger than mine.
- Resolve collisions. I.e. don't made pieces run into each other, don't over-overkill (any damage above 255 on a tile is wasted), don't let strong pieces stay too long in the middle (it's ok for a strong piece to move out and gobble a small piece, but not vice versa), if you're gonna get gobbled up anyways, might as well move into battle even if not strong enough.
