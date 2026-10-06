# Goplanska Party Markers 1.0.0

NeoForge 1.21.1 client addon for WAY 2.0.0. Uses WAY's synchronized player locations, including players outside entity/terrain render distance. Coordinates are projected with the live camera matrices; dots are drawn in the HUD at radius 2 GUI pixels plus a 1 pixel dark border. Distance, FOV and perspective never change the dot radius. Only players in front of the camera and on screen in the same dimension are marked. HUD hiding (F1) hides dots. WAY's M toggle still controls its separate body outlines; party dots stay on.

Server membership is saved in the vanilla `goplanska_party` dummy scoreboard objective, independent of vanilla role teams. Group 1: aridlin and SzybkiOrzech. Group 2: rozowykocurek and Kameleon1200. The datapack initializes scores only when absent, so reloads do not reset membership. WAY colors and the server's same-color visibility filter bridge those memberships into position updates. Unassigned players have no party dot. The configured range is 999999 blocks, covering the 200000-block world border.

Administrator membership commands:
- `scoreboard players set NAME goplanska_party 1`
- `scoreboard players set NAME goplanska_party 2`
- `scoreboard players set NAME goplanska_party 0` to remove membership.

Build locally with `python build.py` using the existing NeoForge 21.1.252 mapped development cache. Source and dependencies remain separate; the jar contains only this addon. `test/ProjectionTest.java` verifies camera projection through 999999 blocks, behind-camera clipping and invalid inputs. An in-game visual check is still required after installation.
