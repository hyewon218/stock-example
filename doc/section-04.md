# Redis 이용해보기
분산 Lock을 구현할 때 대표적인 라이브러리
## Lettuce
- setnx 명령어를 활용하여 분산 Lock 구현
    - setnx(**set if not exist=set if absent**) : 데이터베이스에 동일한 key가 없을 경우에만 저장
- spin lock 방식
    - spin lock : Lock을 획득하려는 쓰레드가 획득 가능한지 **반복적으로 확인**하면서 Lock 획득을 시도하는 방법
    - retry 로직을 개발자가 작성해줘야한다.
    - 로직 실행 전에 key와 setnx 명령어를 활용해서 락을 하고 로직이 끝나면 언락 메소드를 통해 락을 해제

<img src="https://github.com/hyewon218/stock-example/assets/126750615/0b369f12-49cf-45bc-878f-8133304a0740" width="60%"/><br>
<img src="https://github.com/hyewon218/stock-example/assets/126750615/3549460a-0084-4e04-82a3-23be6d9ddac8" width="60%"/><br>
> 쓰레드 1이`setnx 1`명령어를 통해 key가 1인 Lock을 획득, 이후에 쓰레드 2가`setnx 1`명령어를 통해 Lock을 획득하려하지만 이미 존재하므로 실패, 일정시간 이후에 Lock 획득할 때까지 재시도

<br>

## Redisson
- **pub-sub** 기반으로 Lock 구현 제공
    - pub-sub 기반 : 채널을 하나 만들고 Lock을 점유중인 쓰레드에게 해제를 알려주면 안내를 받은 쓰레드가 Lock 획득 시도
    - retry 로직 필요없음
  
<img src="https://github.com/hyewon218/stock-example/assets/126750615/4cc1cf0e-0a47-4a0a-b3f3-f4f078b6e9e4" width="60%"/><br>
```
docker pull redis
docker run --name myredis -d -p 6379:6379 redis
```
<img src="https://github.com/hyewon218/stock-example/assets/126750615/dbf09b90-497a-439f-8246-b845800f0082" width="60%"/><br>
```groovy
dependencies {
    ...
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    ...
}
```

<br>

## Lettuce 이용해보기
```
// Redis-cli 실행
docker exec -it 5c2f6de935ae redis-cli 

// Key가 1, Value가 lock 데이터 set, 처음에는 Key가 1인 데이터가 없으므로 성공, 한번더 수행한다면 실패
127.0.0.1:6379> setnx 1 lock
```
<img src="https://github.com/hyewon218/stock-example/assets/126750615/9b4fdd59-56db-4738-9a40-43950cf536bf" width="60%"/><br>
Lettuce를 이용하는 것은 MySQL의 Named Lock과 거의 동일하다고 볼 수 있다.<br> 
다른 점으로는 Redis를 사용한다는 점과 Session 관리에 신경쓰지 않아도 된다는 점이다.
```java
@Component
@RequiredArgsConstructor
public class RedisLockRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public Boolean lock(Long key) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(generateKey(key), "lock", Duration.ofMillis(3_000));
    }

    public Boolean unlock(Long key) {
        return redisTemplate
                .delete(generateKey(key));
    }

    private String generateKey(Long key) {
        return key.toString();
    }


}
```
```java
@Component
@RequiredArgsConstructor
public class LettuceLockStockFacade {

    private final RedisLockRepository redisLockRepository;

    private final StockService stockService;

    public void decrease(Long key, Long quantity) throws InterruptedException {
        while (!redisLockRepository.lock(key)) {
            Thread.sleep(100);
        }

        try {
            stockService.decrease(key, quantity);
        } finally {
            redisLockRepository.unlock(key);
        }
    }

}
```
```java
@SpringBootTest
class LettuceLockStockFacadeTest {

    ...

    @Test
    public void 동시에_100개의_요청_Lettuce() throws InterruptedException {

        int threadCount = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    lettuceLockStockFacade.decrease(1L, 1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }

            });
        }

        ...

    }
}
```
### 장점
- 구현이 간단하다.

### 단점
- Spin Lock 방식이므로 Redis에 부하를 줄 수 있다. -> Thread.sleep을 이용하여 Lock 획득 재시도 사이에 텀을 줘야한다.
<img src="https://github.com/hyewon218/stock-example/assets/126750615/cce9b9a6-44d0-46b6-95f5-07094a8c28cc" width="60%"/><br>

<br>

## Redisson 이용
```groovy
dependencies {
    ...
    // https://mvnrepository.com/artifact/org.redisson/redisson-spring-boot-starter
implementation 'org.redisson:redisson-spring-boot-starter:3.24.3'
    ...

}
```
<img src="https://github.com/hyewon218/stock-example/assets/126750615/256b3ead-7919-4a52-8369-2d06b9dd692d" width="60%"/><br>

<br>

### pub-sub 실습
터미널 2개를 이용하여 pub-sub 실습
<img src="https://github.com/hyewon218/stock-example/assets/126750615/c71de999-dca2-403e-a6aa-a3d61b12a4ee" width="60%"/><br>
- ch1 채널을 구독하는 있는 곳에서 hello라는 메세지를 받는 것을 확인 할 수 있다.
> 🧑🏻‍🏫 Redisson은 Lock을 해제할 때 메시지를 보내줌으로써 Lock을 획득하기를 원하는 쓰레드들에게 전달해줄 수 있다.<br>
> Lettuce는 지속적으로 Lock 획득 요청을 보내는 반면에 Redisson은 Lock 해제가 되었을 때 한번 혹은 몇 번만 시도 요청을 보내게되므로 Redis의 부하를 줄여줄 수 있다.<br>
> Redisson은 락 관련된 클래스들을 라이브러리에서 제공을 해주므로 별도의 레포지토리를 작성하지 않아도 된다.
```java
@Component
@RequiredArgsConstructor
public class RedissonLockStockFacade {

    private final RedissonClient redissonClient;

    private final StockService stockService;

    public void decrease(Long key, Long quantity) throws InterruptedException {
        RLock lock = redissonClient.getLock(key.toString()); // key를 통해 Rock 객체 획득

        try {
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);// Rock 획득 시도, 몇 초동안 Lock 획득을 시도할 것인지 ? 몇 초동안 점유할 것인지?

            if (!available) {
                System.out.println("Lock 획득 실패");
                return;
            }
            stockService.decrease(key, quantity);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

}
```
```java
@Test
    public void 동시에_100개의_요청_Redisson() throws InterruptedException {

        int threadCount = 100;

        // ExecutorService: 비동기로 실행하는 작업을 단순하하여 사용할 수 있게 도와주는 자바의 API
        ExecutorService executorService = Executors.newFixedThreadPool(32);

        // CountDownLatch: 다른 스레드에서 수행중인 작업이 모두 완료될 때까지 대기할 수 있도록 도와주는 클래스
        // 100개의 요청이 끝날때까지 기다려야하므로 CountDownLatch 를 사용
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    redissonLockStockFacade.decrease(1L, 1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }

            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();
        assertEquals(0L, stock.getQuantity());
    }
```
### 장점
- pub-sub 기반이므로 Redis에 부하를 줄여줄 수 있다.
### 단점
- Lettuce에 비해서 구현이 조금 **복잡**하다.
- 별도의 라이브러리를 사용해야 한다.

<img src="https://github.com/hyewon218/stock-example/assets/126750615/47e563f6-c6e6-441e-b5a1-b04a4f9718f1" width="60%"/><br>

<br>

# 라이브러리 장단점
## Lettuce
- 구현이 **간단**하다
- spring data redis를 이용하면 lettuce가 기본이기 때문에 별도의 라이브러리를 사용하지 않아도 된다.
- spin lock 방식이기때문에 동시에 많은 스레드가 lock 획득 대기 상태라면 redis에 **부하**가 갈 수 있다.

## Redisson
- **락 획득 재시도**를 기본으로 제공한다.
- **pub-sub** 방식으로 구현이 되어있기 때문에 lettuce 와 비교했을 때 redis 에 **부하가 덜 간다.**
- **별도의 라이브러리**를 사용해야한다.
- lock 을 라이브러리 차원에서 제공해주기 떄문에 **사용법을 공부해야** 한다.

## 실무에서는?
- 재시도가 필요하지 않은 lock 은 lettuce 활용
- **재시도**가 필요한 경우에는 **redisson**를 활용

<br>

# MySQL과 Redis의 장단점
## MySQL
- 이미 MySQL 을 사용하고 있다면 별도의 비용없이 사용가능하다.
- 어느정도의 트래픽까지는 문제없이 활용이 가능하다.
- Redis 보다는 성능이 좋지않다.

## Redis
- 활용중인 Redis 가 없다면 별도의 구축**비용**과 인프라 관리비용이 발생한다.
- MySQL 보다 **성능**이 좋다.