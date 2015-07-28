package com.miloshpetrov.sol2.game.chunk;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.miloshpetrov.sol2.Const;
import com.miloshpetrov.sol2.TextureManager;
import com.miloshpetrov.sol2.common.SolColor;
import com.miloshpetrov.sol2.common.SolMath;
import com.miloshpetrov.sol2.game.*;
import com.miloshpetrov.sol2.game.asteroid.FarAsteroid;
import com.miloshpetrov.sol2.game.dra.*;
import com.miloshpetrov.sol2.game.input.*;
import com.miloshpetrov.sol2.game.maze.Maze;
import com.miloshpetrov.sol2.game.planet.*;
import com.miloshpetrov.sol2.game.ship.*;
import com.miloshpetrov.sol2.game.ship.hulls.HullConfig;

import java.util.ArrayList;

public class ChunkFiller {
  public static final float DUST_DENSITY = .2f;
  public static final float ASTEROID_DENSITY = .008f;
  public static final float MIN_SYS_A_SZ = .5f;
  public static final float MAX_SYS_A_SZ = 1.2f;
  public static final float MIN_BELT_A_SZ = .4f;
  public static final float MAX_BELT_A_SZ = 2.4f;
  private static final float MAX_A_SPD = .2f;

  private static final float BELT_A_DENSITY = .04f;

  public static final float JUNK_MAX_SZ = .3f;
  public static final float JUNK_MAX_ROT_SPD = 45f;
  public static final float JUNK_MAX_SPD_LEN = .3f;

  public static final float FAR_JUNK_MAX_SZ = 2f;
  public static final float FAR_JUNK_MAX_ROT_SPD = 10f;

  public static final float ENEMY_MAX_SPD = .3f;
  public static final float ENEMY_MAX_ROT_SPD = 15f;
  public static final float DUST_SZ = .02f;
  private static final float MAZE_ZONE_BORDER = 20;
  private final TextureAtlas.AtlasRegion myDustTex;

  public ChunkFiller(TextureManager textureManager) {
    myDustTex = textureManager.getTex("deco/space/dust", null);
  }

  /**
   * Fill the background of a given chunk with floating junk.
   *
   * @param game    The {@link SolGame} instance to work with
   * @param chunk   The coordinates of the chunk
   * @param remover
   * @param farBg   Determines which of the background layers should be filled. <code>true</code> fills the layers furthest away, <code>false</code> fills the closer one.
   */
  public void fill(SolGame game, Vector2 chunk, RemoveController remover, boolean farBg) {
    if (DebugOptions.NO_OBJS) return;

    // Determine the center of the chunk by multiplying the chunk coordinates with the chunk size and adding half a chunk's size
    Vector2 chCenter = new Vector2(chunk);
    chCenter.scl(Const.CHUNK_SIZE);
    chCenter.add(Const.CHUNK_SIZE / 2, Const.CHUNK_SIZE / 2);

    // Define the default density multiplier for different layers of junk in the far background
    float[] densityMul = {1};

    // Get the environment configuration
    SpaceEnvConfig conf = getConfig(game, chCenter, densityMul, remover, farBg);

    if (farBg) {
      fillFarJunk(game, chCenter, remover, DraLevel.FAR_DECO_3, conf, densityMul[0]);
      fillFarJunk(game, chCenter, remover, DraLevel.FAR_DECO_2, conf, densityMul[0]);
      fillFarJunk(game, chCenter, remover, DraLevel.FAR_DECO_1, conf, densityMul[0]);
    } else {
      fillDust(game, chCenter, remover);
      fillJunk(game, remover, conf, chCenter);
    }
  }

  /**
   * Retrieves an environmental configuration based on the position of the chunk relative to planets, asteroid belts,
   * solar systems and similar things. Also, where necessary, adjust the density of objects in the chunk.
   * <p/>
   * TODO: This also prompts the creation of asteroids and enemies in certain conditions. It might be advisable to
   * move those parts to a dedicated and therefore less obscure method.
   *
   * @param game       The {@link SolGame} instance to work with
   * @param chCenter   The center of the chunk
   * @param densityMul A density multiplier based on the environment <i>may</i> be written to <code>densityMul[0]</code>.
   * @param remover
   * @param farBg      Determines which of the background layers should be filled. <code>true</code> fills the layers furthest away, <code>false</code> fills the closer one.
   * @return Returns an environmental configuration as described above or <code>null</code>, if none of the cases are applicable.
   */
  private SpaceEnvConfig getConfig(SolGame game, Vector2 chCenter, float[] densityMul,
                                   RemoveController remover, boolean farBg) {
    // Find the distance to the closest solar system
    PlanetManager pm = game.getPlanetMan();
    SolSystem sys = pm.getNearestSystem(chCenter);
    float toSys = sys.getPos().dst(chCenter);

    // Check whether the center of the lies inside a solar system
    if (toSys < sys.getRadius()) {
      // There is no decoration behind a sun
      if (toSys < Const.SUN_RADIUS) return null;

      for (SystemBelt belt : sys.getBelts()) {
        // If the center of the chunk lies inside an asteroid belt:
        if (belt.contains(chCenter)) {
          // Fill chunk with asteroid belt-size asteroids if we're not currently handling the far background
          // TODO: Consider handling this in a less obscure location
          if (!farBg) fillAsteroids(game, remover, true, chCenter);

          // Get the system configuration specific to this belt
          SysConfig beltConfig = belt.getConfig();

          // Add temporary enemy ships specific to this belt
          for (ShipConfig enemyConf : beltConfig.tempEnemies) {
            if (!farBg) fillEnemies(game, remover, enemyConf, chCenter);
          }

          // Return this belt's environmental configuration
          return beltConfig.envConfig;
        }
      }

      // If the center of the chunk does NOT lie inside an asteroid belt
      // Determine the density multiplier based on the distance of the center to the solar system
      float perc = toSys / sys.getRadius() * 2;
      if (perc > 1) perc = 2 - perc;
      densityMul[0] = perc;

      // If a background other than the far background is being handled and there isn't a planet nearby, fill the chunk
      // with asteroids and enemies.
      // TODO: Consider handling this in a less obscure location
      if (!farBg) {
        Planet p = pm.getNearestPlanet(chCenter);
        float toPlanet = p.getPos().dst(chCenter);
        boolean planetNear = toPlanet < p.getFullHeight() + Const.CHUNK_SIZE;
        if (!planetNear) fillForSys(game, chCenter, remover, sys);
      }

      return sys.getConfig().envConfig;
    }

    // If the center of the chunk lies outside of a solar system but within the zone immediately surrounding a maze
    Maze m = pm.getNearestMaze(chCenter);
    float dst = m.getPos().dst(chCenter);
    float zoneRad = m.getRadius() + MAZE_ZONE_BORDER;
    if (dst < zoneRad) {
      // Set the density multiplier based on the distance to the maze and return the environmental configuration
      densityMul[0] = 1 - dst / zoneRad;
      return m.getConfig().envConfig;
    }

    return null;
  }

  private void fillForSys(SolGame game, Vector2 chCenter, RemoveController remover, SolSystem sys) {
    SysConfig conf = sys.getConfig();
    Vector2 mainStationPos = game.getGalaxyFiller().getMainStationPos();
    Vector2 startPos = mainStationPos == null ? new Vector2() : mainStationPos;
    float dst = chCenter.dst(startPos);
    if (Const.CHUNK_SIZE < dst) {
      fillAsteroids(game, remover, false, chCenter);
      ArrayList<ShipConfig> enemies = sys.getPos().dst(chCenter) < sys.getInnerRad() ? conf.innerTempEnemies : conf.tempEnemies;
      for (ShipConfig enemyConf : enemies) {
        fillEnemies(game, remover, enemyConf, chCenter);
      }
    }
  }

  private void fillEnemies(SolGame game, RemoveController remover, ShipConfig enemyConf, Vector2 chCenter) {
    int count = getEntityCount(enemyConf.density);
    if (count == 0) return;
    for (int i = 0; i < count; i++) {
      Vector2 enemyPos = getFreeRndPos(game, chCenter);
      FarShip ship = buildSpaceEnemy(game, enemyPos, remover, enemyConf);
      if (ship != null) game.getObjMan().addFarObjNow(ship);
    }
  }

  public FarShip buildSpaceEnemy(SolGame game, Vector2 pos, RemoveController remover,
                                 ShipConfig enemyConf) {
    if (pos == null) return null;
    Vector2 spd = new Vector2();
    SolMath.fromAl(spd, SolMath.rnd(180), SolMath.rnd(0, ENEMY_MAX_SPD));
    float rotSpd = SolMath.rnd(ENEMY_MAX_ROT_SPD);
    MoveDestProvider dp = new StillGuard(pos, game, enemyConf);
    Pilot provider = new AiPilot(dp, false, Fraction.EHAR, true, null, Const.AI_DET_DIST);
    HullConfig config = enemyConf.hull;
    int money = enemyConf.money;
    float angle = SolMath.rnd(180);
    return game.getShipBuilder().buildNewFar(game, pos, spd, angle, rotSpd, provider, enemyConf.items, config,
        remover, false, money, null, true);
  }

  private void fillAsteroids(SolGame game, RemoveController remover, boolean forBelt, Vector2 chCenter) {
    float density = forBelt ? BELT_A_DENSITY : ASTEROID_DENSITY;
    int count = getEntityCount(density);
    if (count == 0) return;
    for (int i = 0; i < count; i++) {
      Vector2 asteroidPos = getFreeRndPos(game, chCenter);
      if (asteroidPos == null) continue;
      float minSz = forBelt ? MIN_BELT_A_SZ : MIN_SYS_A_SZ;
      float maxSz = forBelt ? MAX_BELT_A_SZ : MAX_SYS_A_SZ;
      float sz = SolMath.rnd(minSz, maxSz);
      Vector2 spd = new Vector2();
      SolMath.fromAl(spd, SolMath.rnd(180), MAX_A_SPD);

      FarAsteroid a = game.getAsteroidBuilder().buildNewFar(asteroidPos, spd, sz, remover);
      game.getObjMan().addFarObjNow(a);
    }
  }

  /**
   * Add a bunch of a certain type of junk to the background layers furthest away.
   * <p/>
   * This type of junk does not move on its own, it merely changes position as the camera moves, simulating different
   * depths relative to the camera.
   *
   * @param game       The {@link SolGame} instance to work with
   * @param chCenter   The center of the chunk
   * @param remover
   * @param draLevel   The depth of the junk
   * @param conf       The environment configuration
   * @param densityMul A density multiplier. This will be multiplied with the density defined in the environment configuration
   */
  private void fillFarJunk(SolGame game, Vector2 chCenter, RemoveController remover, DraLevel draLevel,
                           SpaceEnvConfig conf, float densityMul) {
    if (conf == null) return;
    int count = getEntityCount(conf.farJunkDensity * densityMul);
    if (count == 0) return;

    ArrayList<Dra> dras = new ArrayList<Dra>();
    TextureManager textureManager = game.getTexMan();

    for (int i = 0; i < count; i++) {
      // Select a random far junk texture
      TextureAtlas.AtlasRegion tex = SolMath.elemRnd(conf.farJunkTexs);
      // Flip texture for every other piece of junk
      if (SolMath.test(.5f)) tex = textureManager.getFlipped(tex);
      // Choose a random size (within a range)
      float sz = SolMath.rnd(.3f, 1) * FAR_JUNK_MAX_SZ;
      // Apply a random rotation speed
      float rotSpd = SolMath.rnd(FAR_JUNK_MAX_ROT_SPD);
      // Select a random position in the chunk centered around chCenter, relative to the position of the chunk.
      Vector2 junkPos = getRndPos(chCenter);
      junkPos.sub(chCenter);

      // Create the resulting sprite and add it to the list
      RectSprite s = new RectSprite(tex, sz, 0, 0, junkPos, draLevel, SolMath.rnd(180), rotSpd, SolColor.DDG, false);
      dras.add(s);
    }

    // Create a common FarDras instance for the pieces of junk and only allow the junk to be drawn when it's not hidden by a planet
    FarDras so = new FarDras(dras, new Vector2(chCenter), new Vector2(), remover, true);
    // Add the collection of objects to the object manager
    game.getObjMan().addFarObjNow(so);
  }

  /**
   * Add a bunch of a certain type of junk to the background layer closest to the front.
   * <p/>
   * This type of junk moves at the same speed as the camera (similar to the dust) but additionally has its own floating
   * direction and angle for every individual piece of junk.
   *
   * @param game     The {@link SolGame} instance to work with
   * @param remover
   * @param conf     The environment configuration
   * @param chCenter The center of the chunk
   */
  private void fillJunk(SolGame game, RemoveController remover, SpaceEnvConfig conf, Vector2 chCenter) {
    if (conf == null) return;
    int count = getEntityCount(conf.junkDensity);
    if (count == 0) return;

    for (int i = 0; i < count; i++) {
      // Select a random position in the chunk centered around chCenter, relative to the entire map.
      Vector2 junkPos = getRndPos(chCenter);

      // Select a random junk texture
      TextureAtlas.AtlasRegion tex = SolMath.elemRnd(conf.junkTexs);
      // Flip texture for every other piece of junk
      if (SolMath.test(.5f)) tex = game.getTexMan().getFlipped(tex);
      // Choose a random size (within a range)
      float sz = SolMath.rnd(.3f, 1) * JUNK_MAX_SZ;
      // Apply a random rotation speed
      float rotSpd = SolMath.rnd(JUNK_MAX_ROT_SPD);

      // Create the resulting sprite and add it to the list as the only element
      RectSprite s = new RectSprite(tex, sz, 0, 0, new Vector2(), DraLevel.DECO, SolMath.rnd(180), rotSpd, SolColor.LG, false);
      ArrayList<Dra> dras = new ArrayList<Dra>();
      dras.add(s);

      // Create a FarDras instance for this piece of junk and only allow it to be drawn when it's not hidden by a planet
      Vector2 spd = new Vector2();
      SolMath.fromAl(spd, SolMath.rnd(180), SolMath.rnd(JUNK_MAX_SPD_LEN));
      FarDras so = new FarDras(dras, junkPos, spd, remover, true);
      // Add the object to the object manager
      game.getObjMan().addFarObjNow(so);
    }
  }

  /**
   * Add specks of dust to the background layer closest to the front.
   * <p/>
   * Dust is fixed in the world and therefore moves opposite to the cameras movement.
   *
   * @param game     The {@link SolGame} instance to work with
   * @param chCenter The center of the chunk
   * @param remover
   */
  private void fillDust(SolGame game, Vector2 chCenter, RemoveController remover) {
    ArrayList<Dra> dras = new ArrayList<Dra>();
    int count = getEntityCount(DUST_DENSITY);
    if (count == 0) return;

    TextureAtlas.AtlasRegion tex = myDustTex;
    for (int i = 0; i < count; i++) {
      // Select a random position in the chunk centered around chCenter, relative to the position of the chunk.
      Vector2 dustPos = getRndPos(chCenter);
      dustPos.sub(chCenter);
      // Create the resulting sprite and add it to the list
      RectSprite s = new RectSprite(tex, DUST_SZ, 0, 0, dustPos, DraLevel.DECO, 0, 0, SolColor.W, false);
      dras.add(s);
    }

    // Create a common FarDras instance for the specks of dust and only allow the dust to be drawn when it's not hidden by a planet
    FarDras so = new FarDras(dras, chCenter, new Vector2(), remover, true);
    game.getObjMan().addFarObjNow(so);
  }

  /**
   * Find a random position in a chunk centered around chCenter, relative to the entire map, and make sure it is not yet
   * occupied by another entity.
   * <p/>
   * Up to 100 tries will be made to find an unoccupied position; if by then none has been found, <code>null</code> will be returned.
   *
   * @param g        The {@link SolGame} instance to work with
   * @param chCenter The center of a chunk in which a random position should be found
   * @return A random, unoccupied position in a chunk centered around chCenter, relative to the entire map, or <code>null</code> if within 100 tries no unoccupied position has been found
   */
  private Vector2 getFreeRndPos(SolGame g, Vector2 chCenter) {
    for (int i = 0; i < 100; i++) {
      Vector2 pos = getRndPos(new Vector2(chCenter));
      if (g.isPlaceEmpty(pos, true)) return pos;
    }
    return null;
  }

  /**
   * Returns a random position in a chunk centered around chCenter, relative to the entire map.
   *
   * @param chCenter The center of a chunk in which a random position should be found
   * @return A random position in a chunk centered around chCenter, relative to the entire map.
   */
  private Vector2 getRndPos(Vector2 chCenter) {
    Vector2 pos = new Vector2(chCenter);
    pos.x += SolMath.rnd(Const.CHUNK_SIZE / 2);
    pos.y += SolMath.rnd(Const.CHUNK_SIZE / 2);
    return pos;
  }

  /**
   * Determine the number of objects per chunk for a given density, based on the chunk size.
   * If the number turns out to be less than 1, 1 will be returned randomly with a probability of the resulting number, otherwise 0.
   *
   * @param density The density of the objects per chunk
   * @return The number of objects for the chunk based on the given density.
   */
  private int getEntityCount(float density) {
    float amt = Const.CHUNK_SIZE * Const.CHUNK_SIZE * density;
    if (amt >= 1) return (int) amt;
    return SolMath.test(amt) ? 1 : 0;
  }

}
