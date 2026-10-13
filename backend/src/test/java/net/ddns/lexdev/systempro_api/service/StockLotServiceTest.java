package net.ddns.lexdev.systempro_api.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
import org.junit.jupiter.api.*; import org.springframework.test.util.ReflectionTestUtils;
import net.ddns.lexdev.systempro_api.domain.*; import net.ddns.lexdev.systempro_api.enums.*; import net.ddns.lexdev.systempro_api.repository.*;
class StockLotServiceTest {
 private StockLotRepository lots; private StockMovementLotRepository allocations; private StockSettingsService settings; private StockLotService service;
 @BeforeEach void setUp(){lots=mock(StockLotRepository.class);allocations=mock(StockMovementLotRepository.class);settings=mock(StockSettingsService.class);when(settings.minSellableShelfLifeDays()).thenReturn(1);service=new StockLotService(lots,allocations,settings);}
 @Test void consumesOldestEligibleExpirationFirst(){
  Product p=product(); StockMovement m=movement(p); StockLot first=lot(p,"A",LocalDate.now().plusDays(2),"3"); StockLot second=lot(p,"B",LocalDate.now().plusDays(5),"5");
  when(lots.findAvailableForUpdateFefo(1L)).thenReturn(List.of(first,second));
  service.consumeFefo(p,m,new BigDecimal("4"),false);
  assertThat(first.getQuantity()).isEqualByComparingTo("0"); assertThat(second.getQuantity()).isEqualByComparingTo("4"); verify(allocations,times(2)).save(any());
 }
 @Test void blocksLotThatExpiresBeforeConfiguredMinimum(){
  Product p=product(); StockLot today=lot(p,"HOJE",LocalDate.now(),"5"); when(lots.findAvailableForUpdateFefo(1L)).thenReturn(List.of(today));
  assertThatThrownBy(()->service.consumeFefo(p,movement(p),BigDecimal.ONE,false)).hasMessageContaining("validade mínima suficiente").hasMessageContaining("mínimo: 1 dia");
  assertThat(today.getQuantity()).isEqualByComparingTo("5"); verifyNoInteractions(allocations);
 }
 @Test void allowsLotThatExpiresTodayWhenMinimumIsZero(){
  when(settings.minSellableShelfLifeDays()).thenReturn(0); Product p=product(); StockLot today=lot(p,"HOJE",LocalDate.now(),"2"); when(lots.findAvailableForUpdateFefo(1L)).thenReturn(List.of(today));
  service.consumeFefo(p,movement(p),BigDecimal.ONE,false); assertThat(today.getQuantity()).isEqualByComparingTo("1"); verify(allocations).save(any());
 }
 @Test void negativeAdjustmentCanConsumeExpiredLot(){
  Product p=product(); StockLot expired=lot(p,"VENCIDO",LocalDate.now().minusDays(1),"2"); when(lots.findAvailableForUpdateFefo(1L)).thenReturn(List.of(expired));
  service.consumeFefo(p,movement(p),BigDecimal.ONE,true); assertThat(expired.getQuantity()).isEqualByComparingTo("1"); verifyNoInteractions(settings);
 }
 @Test void rejectsTrueLotShortage(){Product p=product();StockLot first=lot(p,"A",LocalDate.now().plusDays(2),"1");when(lots.findAvailableForUpdateFefo(1L)).thenReturn(List.of(first));assertThatThrownBy(()->service.consumeFefo(p,movement(p),new BigDecimal("2"),false)).hasMessageContaining("Saldo por lote insuficiente");}
 private Product product(){Product p=new Product();ReflectionTestUtils.setField(p,"id",1L);p.setCode("P1");p.setControlsStock(true);p.setControlsExpiration(true);return p;}
 private StockLot lot(Product p,String code,LocalDate expiration,String qty){StockLot l=new StockLot(p,code,null,expiration);l.setQuantity(new BigDecimal(qty));return l;}
 private StockMovement movement(Product p){return new StockMovement(p,StockMovementType.SALE_OUT,StockMovementOrigin.SALE,"ref",BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("9"),null,null,"x",new User());}
}