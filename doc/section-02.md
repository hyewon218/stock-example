# Synchronized 이용해보기
메서드 선언 부에 synchronized를 붙여주게 되면 해당 메서드는 1개의 쓰레드만 접근이 가능하게 된다.
```java
 @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void decrease(Long id, Long quantity) {
        // Stock 조회
        // 재고감소
        // 저장

        Stock stock = stockRepository.findById(id).orElseThrow();

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
```

<br>

### 위와 같이`synchronized`를 붙였음에도 테스트 실패
<img src="https://github.com/hyewon218/stock-example/assets/126750615/ae31b3e8-d57c-4a98-b3f4-3900148c90fe" width="60%"/><br>
Spring의`@Transactional`어노테이션의 동작방식 때문이다.<br>
Spring에서는`@Transactional`어노테이션을 이용하면 우리가 만든 클래스를 wrapping한 클래스를 새로만들어서 실행한다.<br>
간단하게 설명하면<br>

```java
public class TransactionService {

    private StockService stockService;

    public TransactionService(StockService stockService) {
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) {

        startTransaction();

        stockService.decrease(id, quantity);

        endTransaction();

    }

    private void startTransaction() {
        ...
    }

    private void endTransaction() {
        ...
    }
}
```
> 🧑🏻‍🏫 위와 같이 StockService를 주입받은 새로운 클래스를 만들어서 실행한다.<br>
> StockService의 decrease 메서드가 수행되면 transaction을 시작하고 Stockservice의 decrease 메서드를 호출하여 재고를 감소 시킨 뒤 transaction을 종료한다.<br>
> transaction의 종료시점에 DB를 업데이트하는데 여기서 문제가 발생한다.<br>
> decrease 메서드가 완료되었고 실제 DB 업데이트 전에 새로운 쓰레드가 decrease 메서드를 호출할 수 있다.<br>
> 그렇게되면 다른쓰레드는 갱신되기전 값을 가져가게 되어 누락 문제가 발생하게 되는것이다.

### `@Transaction`어노테이션을 삭제하면 문제가 해결된다.
<img src="https://github.com/hyewon218/stock-example/assets/126750615/db3a1a35-409d-44ec-81eb-e3c60c575ad1" width="60%"/><br>

<br>

## java synchronized 문제점
<img src="https://github.com/hyewon218/stock-example/assets/126750615/5853ba35-914a-4191-98fd-ab7902376bd2" width="60%"/><br>
> 🧑🏻‍🏫 자바의`synchronized`는 하나의 프로세스에서만 보장이 된다.<br>
> 서버가 1대인 경우에는 데이터의 접근을 서버 하나에서만 하므로 문제가 발생하지 않지만 서버가 두 대 혹은 그 이상일 경우에는 데이터의 접근을 여러 서버에서 할 수 있게 된다.<br>
> 예를 들어<br>

<img src="https://github.com/hyewon218/stock-example/assets/126750615/379bed04-5984-425b-a46a-6b72d2224dc8" width="60%"/><br>
> 🧑🏻‍🏫 서버 1에서 재고 감소를 10시에 시작하여 10시 5분에 종료한다고 할 때<br>
> 서버 2에서 10시 ~ 10시 5분 사이에 갱신되기 전 데이터를 가져가서 새로운 값으로 갱신이 가능해진다.<br>
> 또 레이스 컨디션 발생하게 되는 것이다.<br>
> 실제 서버에서는 대부분 여러 서버를 사용하므로 레이스 컨디션의 해결방법으로 자바의`synchronized`는 잘 사용되지 않는다.<br>
