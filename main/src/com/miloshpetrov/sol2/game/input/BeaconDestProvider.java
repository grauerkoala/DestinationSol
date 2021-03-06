package com.miloshpetrov.sol2.game.input;

import com.badlogic.gdx.math.Vector2;
import com.miloshpetrov.sol2.Const;
import com.miloshpetrov.sol2.game.BeaconHandler;
import com.miloshpetrov.sol2.game.SolGame;
import com.miloshpetrov.sol2.game.ship.hulls.HullConfig;
import com.miloshpetrov.sol2.game.ship.SolShip;

public class BeaconDestProvider implements MoveDestProvider {
  public static final float STOP_AWAIT = .1f;
  private final Vector2 myDest;

  private Boolean myShouldManeuver;
  private boolean myShouldStopNearDest;
  private Vector2 myDestSpd;

  public BeaconDestProvider() {
    myDest = new Vector2();
    myDestSpd = new Vector2();
  }

  @Override
  public void update(SolGame game, Vector2 shipPos, float maxIdleDist, HullConfig hullConfig, SolShip nearestEnemy) {
    BeaconHandler bh = game.getBeaconHandler();
    myDest.set(bh.getPos());
    myShouldManeuver = null;
    BeaconHandler.Action a = bh.getCurrAction();
    if (nearestEnemy != null && a == BeaconHandler.Action.ATTACK) {
      if (shipPos.dst(myDest) < shipPos.dst(nearestEnemy.getPos()) + .1f) myShouldManeuver = true;
    }
    myShouldStopNearDest = STOP_AWAIT < game.getTime() - bh.getClickTime();
    myDestSpd.set(bh.getSpd());
  }

  @Override
  public Vector2 getDest() {
    return myDest;
  }

  @Override
  public Boolean shouldManeuver(boolean canShoot, SolShip nearestEnemy, boolean nearGround) {
    return myShouldManeuver;
  }

  @Override
  public Vector2 getDestSpd() {
    return myDestSpd;
  }

  @Override
  public boolean shouldAvoidBigObjs() {
    return true;
  }

  @Override
  public float getDesiredSpdLen() {
    return Const.MAX_MOVE_SPD;
  }

  @Override
  public boolean shouldStopNearDest() {
    return myShouldStopNearDest;
  }
}
