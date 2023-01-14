package org.example;

import org.hibernate.Session;

import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


public class Main {
    public static void main(String[] args) throws InterruptedException {

        Session session = HibernetUtil.getSessionFactory().openSession();
        session.beginTransaction();
        List<Item> items = session.createQuery( "SELECT i FROM Item i" , Item. class ).setLockMode(LockModeType. PESSIMISTIC_READ ).getResultList();
        List<Item> itemsopt = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            items.add(new Item(0, 0));
            itemsopt.add(new Item(0,0));
            session.save(items.get(i));
            session.save(itemsopt.get(i));
        }
        session.getTransaction().commit();
        long sttime = System.currentTimeMillis();
        potoki(items,session);
        //potoki(itemsopt,session);
        long endtime = System.currentTimeMillis() - sttime;
       // System.out.printf("Время выполнения оптимистичного подхода %d \n",endtime);
        System.out.printf("Время выполнения пессимистичного подхода %d \n",endtime);
        session.close();
    }
    public static void potoki(List<Item> items,Session session) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(4);
        AtomicInteger countErrors = new AtomicInteger();
        for(int i = 0; i < 4; i++){
            new Thread(() -> {
                Session session_t = HibernetUtil.getSessionFactory().openSession();
                Random rnd = new Random();
                for(int j = 0;j < 20000; j++){
                    try {
                        session_t.beginTransaction();
                        Item item = session_t.get(Item.class, rnd.nextInt(40) + 1);
                        item.setValue(item.getValue() + 1);
                        session_t.getTransaction().commit();
                    }
                    catch (OptimisticLockException a){
                        session_t.getTransaction().rollback();
                        j--;
                        countErrors.getAndIncrement();
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("Поток готов");

                session_t.close();
                cdl.countDown();
            }).start();
        }
        cdl.await();
        List res = session.createQuery("select sum(i.value) from Item i").getResultList();
        System.out.println("Сумма всех объектов " + res.get(0));
        System.out.println("Количество ошибок " + countErrors);
    }
}