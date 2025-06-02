# Glyph Sample Programs

This document contains sample Glyph programs demonstrating key language features.

# Glyph Language – Sample Programs Suite

## 1. Hello World
```glyph
print("Hello, Glyph World!")
```

---

## 2. ECS Basics: Entity, Component, System
```glyph
component Health:
    value: int = 100

entity Player:
    Health
    name: string = "Hero"

system PrintHealth when tick:
    for player in Player:
        print(player.name + " has " + str(player.Health.value) + " HP")
```

---

## 3. Event Handling
```glyph
event PlayerDamaged:
    player: Player
    amount: int

system DamageSystem when PlayerDamaged:
    for evt in PlayerDamaged:
        evt.player.Health.value -= evt.amount
        print(evt.player.name + " took " + str(evt.amount) + " damage!")

emit PlayerDamaged(player=Player(), amount=10)
```

---

## 4. Pattern Matching
```glyph
enum GameState:
    Loading
    Playing(level: int)
    GameOver(reason: string)

game_state = GameState.Playing(level=1)

match game_state:
    case GameState.Loading:
        print("Loading...")
    case GameState.Playing(level):
        print("Playing level " + str(level))
    case GameState.GameOver(reason):
        print("Game Over: " + reason)
```

---

## 5. Async/Await
```glyph
async func loadAssets():
    print("Loading assets...")
    await sleep(1000)
    print("Assets loaded!")

await loadAssets()
```

---

## 6. Trait/Interface
```glyph
trait Damageable:
    func take_damage(amount: int)

entity Enemy implements Damageable:
    Health
    func take_damage(amount: int):
        self.Health.value -= amount
        print("Enemy took " + str(amount) + " damage!")

let e = Enemy()
e.take_damage(5)
```

---

## 7. Error Handling
```glyph
try:
    let x = 1 / 0
catch err:
    print("Caught error: " + str(err))
```

---

## 8. Macros
```glyph
macro log_all_fields(entity):
    for field in entity.fields():
        print(field + ": " + str(entity[field]))

let p = Player()
log_all_fields(p)
```

---

## 9. Unit Testing
```glyph
test "player takes damage":
    let p = Player()
    p.Health.value = 100
    p.Health.value -= 10
    assert p.Health.value == 90
```

---

## 10. Small Game Loop (Tower Defense Tick)
```glyph
component Cooldown:
    value: float = 1.5
    timer: float = 0.0

entity Tower:
    Cooldown
    range: int = 3
    target: Enemy = null

system TargetEnemies when tick:
    for tower in Tower:
        enemies = find(Enemy, within tower.range)
        if enemies:
            tower.target = enemies[0]

system FireProjectile when tick:
    for tower in Tower:
        if tower.target and ready(tower.Cooldown):
            emit Projectile(from=tower, to=tower.target)
``` 