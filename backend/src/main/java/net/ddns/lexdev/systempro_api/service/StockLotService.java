package net.ddns.lexdev.systempro_api.service;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.*; import org.springframework.stereotype.Service;
import net.ddns.lexdev.systempro_api.domain.*; import net.ddns.lexdev.systempro_api.exception.BusinessException; import net.ddns.lexdev.systempro_api.repository.*;
@Service
public class StockLotService {
 private final StockLotRepository lots; private final StockMovementLotRepository allocations; private final StockSettingsService settings;
 public StockLotService(StockLotRepository lots, StockMovementLotRepository allocations, StockSettingsService settings){this.lots=lots;this.allocations=allocations;this.settings=settings;}
 public void add(Product product, StockMovement movement, BigDecimal quantity, String lotCode, LocalDate manufactureDate, LocalDate expirationDate){
   if(!product.isControlsExpiration()) return;
   String code=requireLotCode(lotCode); validateDates(manufactureDate,expirationDate);
   StockLot lot=lots.findForUpdate(product.getId(),code,expirationDate).orElseGet(()->lots.save(new StockLot(product,code,manufactureDate,expirationDate)));
   if(lot.getManufactureDate()!=null && manufactureDate!=null && !lot.getManufactureDate().equals(manufactureDate)) throw new BusinessException("O lote informado já existe com outra data de fabricação.");
   lot.setQuantity(lot.getQuantity().add(quantity)); allocations.save(new StockMovementLot(movement,lot,quantity));
 }
 public void validateExistingEntry(Product product, StockMovement movement, BigDecimal quantity, String lotCode, LocalDate manufactureDate, LocalDate expirationDate){
   if(!product.isControlsExpiration()) return;
   String code=requireLotCode(lotCode); validateDates(manufactureDate,expirationDate);
   List<StockMovementLot> existing=allocations.findByMovementIdOrderByLotExpirationDateAscLotIdAsc(movement.getId());
   if(existing.size()!=1){throw new BusinessException("A referência da operação já foi utilizada com dados de lote incompatíveis.");}
   StockMovementLot a=existing.get(0); StockLot l=a.getLot();
   if(a.getQuantity().compareTo(quantity)!=0 || !l.getLotCode().equals(code) || !l.getExpirationDate().equals(expirationDate) || !java.util.Objects.equals(l.getManufactureDate(),manufactureDate))
     throw new BusinessException("A referência da operação já foi utilizada com dados de lote diferentes.");
 }

 public void consumeFefo(Product product, StockMovement movement, BigDecimal quantity, boolean allowExpired){
   if(!product.isControlsExpiration()) return;
   List<StockLot> available = lots.findAvailableForUpdateFefo(product.getId());
   int minimumDays = allowExpired ? Integer.MIN_VALUE : settings.minSellableShelfLifeDays();
   LocalDate minimumExpiration = allowExpired ? null : LocalDate.now().plusDays(minimumDays);
   BigDecimal physicalTotal = available.stream().map(StockLot::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
   List<StockLot> eligible = allowExpired ? available : available.stream().filter(lot -> !lot.getExpirationDate().isBefore(minimumExpiration)).toList();
   BigDecimal eligibleTotal = eligible.stream().map(StockLot::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
   if(eligibleTotal.compareTo(quantity)<0){
     if(!allowExpired && physicalTotal.compareTo(quantity)>=0)
       throw new BusinessException("O produto " + product.getCode() + " possui estoque, mas não há lote com validade mínima suficiente para venda (mínimo: " + minimumDays + (minimumDays == 1 ? " dia)." : " dias)."));
     throw new BusinessException("Saldo por lote insuficiente para o produto " + product.getCode() + ".");
   }
   BigDecimal remaining=quantity;
   for(StockLot lot: eligible){
     if(remaining.signum()==0) break;
     BigDecimal take=lot.getQuantity().min(remaining); if(take.signum()<=0) continue;
     lot.setQuantity(lot.getQuantity().subtract(take)); allocations.save(new StockMovementLot(movement,lot,take)); remaining=remaining.subtract(take);
   }
 }
 public void restoreFrom(StockMovement original, StockMovement returnMovement){
   if(!original.getProduct().isControlsExpiration()) return;
   for(StockMovementLot allocation: allocations.findByMovementIdOrderByLotExpirationDateAscLotIdAsc(original.getId())){
     StockLot lot=lots.findByIdForUpdate(allocation.getLot().getId()).orElseThrow(()->new BusinessException("Lote original da venda não encontrado."));
     lot.setQuantity(lot.getQuantity().add(allocation.getQuantity())); allocations.save(new StockMovementLot(returnMovement,lot,allocation.getQuantity()));
   }
 }
 public List<StockLot> list(Long productId){return lots.findByProductIdOrderByExpirationDateAscIdAsc(productId);}
 private String requireLotCode(String code){if(code==null||code.isBlank())throw new BusinessException("O lote é obrigatório para produto com controle de validade."); String v=code.trim();if(v.length()>100)throw new BusinessException("O lote excede 100 caracteres.");return v;}
 private void validateDates(LocalDate manufacture, LocalDate expiration){if(expiration==null)throw new BusinessException("A data de validade é obrigatória para produto com controle de validade.");if(manufacture!=null&&manufacture.isAfter(expiration))throw new BusinessException("A data de fabricação não pode ser posterior à validade.");}
}