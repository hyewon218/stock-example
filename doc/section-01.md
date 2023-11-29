# 재고시스템 만들어보기

재고 시스템을 개발할 때 재고가 맞지않는 문제가 발생할 수 있다.<br>
해당 문제를 **Synchronized, Database Lock, Redis Distributed Lock**을 이용하여 해결해보자

<br>

## 작업환경 세팅
#### mysql 설치 및 실행
```
 docker pull mysql
 docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=1234 -name mysql mysql
 docker ps
```
<img src="https://github.com/hyewon218/stock-example/assets/126750615/a2361b2f-8490-4a31-b743-5caf751bb1c7" width="60%"/><br>

#### mysql 데이터베이스 생성
```
docker exec -it mysql bash
bash-4.4# mysql -u root -p
mysql> create database stock_example;
mysql> use stock_example; 
```
#### 프로젝트 세팅
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/stock_example
    username: root
    password: 1234

logging:
  level:
    org:
      hibernate:
        SQL: DEBUG
        type:
          descriptor:
            sql:
              BasicBinder: TRACE
```

<br>

## 재고감소시스템
#### Stock Entity
```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private Long quantity;

    public Stock(Long productId, Long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public void decrease(Long quantity) {
        if (this.quantity < 0) {
            throw new RuntimeException("재고는 0개 미만이 될 수 없습니다.");
        }

        this.quantity = this.quantity - quantity;
    }
}
```
#### StockService
```java
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long id, Long quantity) {
				// Stock 조회
        // 재고감소
        // 저장

        Stock stock = stockRepository.findById(id).orElseThrow();

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
}
```
#### 재고감소 Test
```java
@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private PessimisticLockStockService pessimisticLockStockService;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach // 테스트 전 DB에 데이터 넣는 작업
    public void before() {
        Stock stock = new Stock(1L, 100L);

        stockRepository.saveAndFlush(stock);
    }

    @AfterEach // 테스트 후 자동으로 데이터 삭제
    public void after() {
        stockRepository.deleteAll();
    }

    @Test
    public void 재고감소() throws Exception {
        stockService.decrease(1L, 1L);

        // 100 - 1 = 99

        Stock stock = stockRepository.findById(1L).orElseThrow();

        assertEquals(99L, stock.getQuantity());

    }

    @Test
    public void 동시에_100개의_요청() throws Exception {
        int threadCount = 100;

        // ExecutorService : 비동기로 실행하는 작업을 단순하하여 사용할 수 있게 도와주는 자바의 API
        ExecutorService executorService = Executors.newFixedThreadPool(32);

        // CountDownLatch : 다른 스레드에서 수행중인 작업이 모두 완료될 때까지 대기할 수 있도록 도와주는 클래스
        // 100개의 요청이 끝날때까지 기다려야하므로 CountDownLatch 를 사용
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decrease(1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        // 100 - (100 * 1) = 0
        assertEquals(0L, stock.getQuantity());
    }
}
```
<br>

### 문제점
<img src="https://github.com/hyewon218/stock-example/assets/126750615/26c05d92-7a27-4b5c-a690-8fcc88421792" width="60%"/><br>
> 레이스 컨디션(둘 이상의 스레드가 공유 데이터에 엑세스할 수 있고 동시에 변경하려고 할 때 발생하는 문제)이 일어났기 때문에 발생하는 문제이다.

기대하는 상황<br>
<img src="https://github.com/hyewon218/stock-example/assets/126750615/2fed5b82-01cb-4551-94db-35701911a404" width="60%"/><br>
실제 상황<br>
<img src="https://github.com/hyewon218/stock-example/assets/126750615/9e9339aa-416e-4f4f-b9a5-c47b20025760" width="60%"/><br>