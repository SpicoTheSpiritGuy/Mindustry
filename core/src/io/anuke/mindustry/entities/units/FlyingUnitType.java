package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.world.BlockFlag;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

import static io.anuke.mindustry.Vars.world;

public class FlyingUnitType extends UnitType {
    protected static Translator vec = new Translator();

    protected float maxAim = 30f;

    public FlyingUnitType(String name) {
        super(name);
        speed = 0.2f;
        maxVelocity = 2f;
        drag = 0.01f;
        isFlying = true;
    }

    @Override
    public void update(BaseUnit unit) {
        super.update(unit);

        unit.rotation = unit.velocity.angle();
    }

    @Override
    public void draw(BaseUnit unit) {
        Draw.alpha(unit.hitTime / Unit.hitDuration);

        Draw.rect(name, unit.x, unit.y, unit.rotation - 90);

        Draw.alpha(1f);
    }

    @Override
    public void behavior(BaseUnit unit) {
        if(unit.health <= health * retreatPercent &&
                Geometry.findClosest(unit.x, unit.y, world.indexer().getAllied(unit.team, BlockFlag.repair)) != null){
            unit.setState(retreat);
        }
    }


    @Override
    public UnitState getStartState(){
        return attack;
    }

    protected void circle(BaseUnit unit, float circleLength){
        vec.set(unit.target.getX() - unit.x, unit.target.getY() - unit.y);

        if(vec.len() < circleLength){
            vec.rotate((circleLength-vec.len())/circleLength * 180f);
        }

        vec.setLength(speed * Timers.delta());

        unit.velocity.add(vec);
    }

    protected void attack(BaseUnit unit, float circleLength){
        vec.set(unit.target.getX() - unit.x, unit.target.getY() - unit.y);

        float ang = unit.angleTo(unit.target);
        float diff = Angles.angleDist(ang, unit.rotation);

        if(diff > 100f && vec.len() < circleLength){
            vec.setAngle(unit.velocity.angle());
        }else{
            vec.setAngle(Mathf.slerpDelta(unit.velocity.angle(), vec.angle(),  0.44f));
        }

        vec.setLength(speed*Timers.delta());

        unit.velocity.add(vec);
    }

    public final UnitState

    resupply = new UnitState(){
        public void entered(BaseUnit unit) {
            unit.target = null;
        }

        public void update(BaseUnit unit) {
            if(unit.inventory.totalAmmo() + 10 >= unit.inventory.ammoCapacity()){
                unit.state.set(unit, attack);
            }else if(!unit.targetHasFlag(BlockFlag.resupplyPoint)){
                if(unit.timer.get(timerTarget, 20)) {
                    Tile target = Geometry.findClosest(unit.x, unit.y, world.indexer().getAllied(unit.team, BlockFlag.resupplyPoint));
                    if (target != null) unit.target = target.entity;
                }
            }else{
                circle(unit, 20f);
            }
        }
    },
    attack = new UnitState(){
        public void entered(BaseUnit unit) {
            unit.target = null;
        }

        public void update(BaseUnit unit) {
            if(Units.invalidateTarget(unit.target, unit.team, unit.x, unit.y)){
                unit.target = null;
            }

            if(!unit.inventory.hasAmmo()) {
                unit.state.set(unit, resupply);
            }else if (unit.target == null){
                if(unit.timer.get(timerTarget, 20)) {
                    Unit closest = Units.getClosestEnemy(unit.team, unit.x, unit.y,
                            unit.inventory.getAmmo().getRange(), other -> other.distanceTo(unit) < 60f);
                    if(closest != null){
                        unit.target = closest;
                    }else {
                        Tile target = Geometry.findClosest(unit.x, unit.y, world.indexer().getEnemy(unit.team, BlockFlag.resupplyPoint));
                        if (target != null) unit.target = target.entity;
                    }
                }
            }else{
                attack(unit, 150f);

                if (unit.timer.get(timerReload, reload) && Mathf.angNear(unit.angleTo(unit.target), unit.rotation, 13f)
                        && unit.distanceTo(unit.target) < unit.inventory.getAmmo().getRange()) {
                    AmmoType ammo = unit.inventory.getAmmo();
                    unit.inventory.useAmmo();

                    shoot(unit, ammo, Angles.moveToward(unit.rotation, unit.angleTo(unit.target), maxAim), 4f);
                }
            }
        }
    },
    retreat = new UnitState() {
        public void entered(BaseUnit unit) {
            unit.target = null;
        }

        public void update(BaseUnit unit) {
            if(unit.health >= health){
                unit.state.set(unit, attack);
            }else if(!unit.targetHasFlag(BlockFlag.repair)){
                if(unit.timer.get(timerTarget, 20)) {
                    Tile target = Geometry.findClosest(unit.x, unit.y, world.indexer().getAllied(unit.team, BlockFlag.repair));
                    if (target != null) unit.target = target.entity;
                }
            }else{
                circle(unit, 20f);
            }
        }
    };
}