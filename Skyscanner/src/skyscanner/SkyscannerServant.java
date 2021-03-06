/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package skyscanner;

import interfaces.SkyscannerInterface;
import interfaces.TravellerInterface;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import messages.Flight;
import messages.FlightBooking;
import messages.FlightSearch;
import messages.FlightSubscription;
import messages.Hotel;
import messages.HotelBooking;
import messages.HotelSearch;
import messages.HotelSubscription;

/**
 *
 * @author Diogo
 */
public class SkyscannerServant extends UnicastRemoteObject implements SkyscannerInterface {

    private AdminGui adminGui;
    private Database database;
    
    public SkyscannerServant() throws RemoteException {
        this.database = new Database();
        adminGui = new AdminGui(this, database);
        adminGui.setLocationRelativeTo(null);
        adminGui.setVisible(true);
        
        // Dummy data
        database.getFlights().add(new Flight("JJ2020", "TAM", "Curitiba", "São Paulo", "01/01/2016", "11:30am", "12:30pm", 61.50, 1));
        database.getFlights().add(new Flight("JO2030", "GOL", "Curitiba", "São Paulo", "01/01/2016", "14:30am", "16:30pm", 161.50, 1));
        database.getFlights().add(new Flight("JJ2040", "TAM", "Curitiba", "São Paulo", "01/01/2016", "17:30am", "19:30pm", 542.50, 1));
        database.getFlights().add(new Flight("JA2045", "AZUL", "Curitiba", "São Paulo", "01/01/2016", "17:50am", "19:30pm", 52.50, 1));
        database.getFlights().add(new Flight("JA2046", "AZUL", "Curitiba", "São Paulo", "01/01/2016", "18:50am", "20:30pm", 92.50, 1));
        database.getFlights().add(new Flight("JJ2021", "TAM", "Curitiba", "Rio de Janeiro", "01/01/2016", "12:00pm", "13:20pm", 100.25, 100));
        database.getFlights().add(new Flight("JJ2022", "TAM", "Rio de Janeiro", "Curitiba", "07/01/2016", "12:00pm", "13:20pm", 76.25, 100));
        database.getFlights().add(new Flight("JJ2023", "GOL", "São Paulo", "Curitiba", "07/01/2016", "11:30am", "12:30pm", 200.50, 1));
        database.getFlights().add(new Flight("JJ2051", "TAM", "São Paulo", "Curitiba", "07/01/2016", "11:30am", "12:30pm", 230.50, 1));
        database.getFlights().add(new Flight("JJ2089", "GOL", "São Paulo", "Curitiba", "07/01/2016", "14:30am", "15:30pm", 200.50, 1));
        database.getFlights().add(new Flight("JJ2099", "GOL", "São Paulo", "Curitiba", "07/01/2016", "16:30am", "18:30pm", 200.50, 1));
        database.getFlights().add(new Flight("JJ2025", "TAM", "São Paulo", "Rio de Janeiro", "07/01/2016", "17:30am", "18:30pm", 111.50, 100));
        
        database.getHotels().add(new Hotel("Ibis", "Curitiba", 1, 150.00));
        database.getHotels().add(new Hotel("Ibis", "São Paulo", 1, 200.00));
        database.getHotels().add(new Hotel("Ibis", "Rio de Janeiro", 1, 250.00));
        database.getHotels().add(new Hotel("Hilton", "São Paulo", 1, 500.00));
        database.getHotels().add(new Hotel("Sheraton", "São Paulo", 150, 400.00));
        database.getHotels().add(new Hotel("Sheraton", "Rio de Janeiro", 150, 400.00));
        
        TimerTask timerTask = new MyTimerTask();
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, Long.MAX_VALUE);
    }
    
    public class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            System.out.println("Removing expired interests");
            removeExpiredInterests();
        }
    
    }

    @Override
    public synchronized void bookFlight(FlightBooking booking, TravellerInterface travellerInterface) throws RemoteException {
        database.getFlightBookings().add(booking);
        int index = 0;
        boolean foundDepartureFlight = false;
        boolean foundReturnFlight = false;
        
        for (Flight flight : database.getFlights()) {
            if (flight.getFlightNumber().equals(booking.getDepartingFlightNumber()) && flight.getAvailableSeats() >= booking.getPassengers().size()) {
                flight.setAvailableSeats(flight.getAvailableSeats() - booking.getPassengers().size());
                database.getFlights().set(index, flight);
                foundDepartureFlight = true;
            }
                
            if (booking.isRoundTrip() && flight.getFlightNumber().equals(booking.getReturningFlightNumber())) {
                flight.setAvailableSeats(flight.getAvailableSeats() - booking.getPassengers().size());
                database.getFlights().set(index, flight);
                foundReturnFlight = true;
            }
            index++;
        }
        
        if( !foundDepartureFlight || !foundReturnFlight){
            travellerInterface.displayFlightBookingConfirmation(booking, false);
        } else {
            travellerInterface.displayFlightBookingConfirmation(booking, true); 
        }
        
        
    }

    @Override
    public synchronized void bookHotel(HotelBooking booking, TravellerInterface travellerInterface) throws RemoteException {
        database.getHotelBookings().add(booking);
        int index = 0;
        
        for (Hotel hotel : database.getHotels()) {
            if (hotel.getHotelId().equals(booking.getHotelId()) && hotel.getAvailableRooms() >= booking.getNumberOfRooms()) {
                hotel.setAvailableRooms(hotel.getAvailableRooms() - booking.getGuests().size());
                database.getHotels().set(index, hotel);
                travellerInterface.displayHotelBookingConfirmation(booking, true);
                return;
            }
            index++;
        }
        travellerInterface.displayHotelBookingConfirmation(booking, false);
        
    }

    @Override
    public void searchFlights(FlightSearch flightSearch, TravellerInterface travellerInterface) throws RemoteException {
        ArrayList<Flight> departingFlights = new ArrayList();
        ArrayList<Flight> returningFlights = new ArrayList();
        
        for (Flight flight : database.getFlights()) {
            if (flightSearch.getOrigin().equals(flight.getOrigin()) &&
                flightSearch.getDestination().equals(flight.getDestination()) &&
                flightSearch.getDepartureDate().equals(flight.getDepartureDate()) &&
                flightSearch.getNumberOfPassengers() <= flight.getAvailableSeats()
                ) {
                System.out.println("Flight: " + flight.getFlightNumber());
                System.out.println("Available seats: " + flight.getAvailableSeats());
                departingFlights.add(flight);
            }
            else if (flightSearch.isRoundTrip() &&
                     flightSearch.getDestination().equals(flight.getOrigin()) &&
                     flightSearch.getOrigin().equals(flight.getDestination()) &&
                     flightSearch.getReturnDate().equals(flight.getDepartureDate()) &&
                     flightSearch.getNumberOfPassengers() <= flight.getAvailableSeats()
                     ) {
                System.out.println("Flight: " + flight.getFlightNumber());
                System.out.println("Available seats: " + flight.getAvailableSeats());
                returningFlights.add(flight);
            }
        }
        
        travellerInterface.getQueriedFlights(departingFlights, returningFlights);
    }

    @Override
    public void searchHotels(HotelSearch hotelSearch, TravellerInterface travellerInterface) throws RemoteException {
        ArrayList<Hotel> hotels = new ArrayList();
        
        for (Hotel hotel : database.getHotels()) {
            if (hotelSearch.getCity().equals(hotel.getCity()) &&
                hotelSearch.getNumberOfRooms() <= hotel.getAvailableRooms()
                ) {
                hotels.add(hotel);
            }
        }
        
        travellerInterface.getQueriedHotels(hotels);
    }

    @Override
    public synchronized void subscribeToFlight(FlightSubscription subscription, TravellerInterface travellerInterface) throws RemoteException {
        database.getFlightSubscriptions().add(subscription);
    }

    @Override
    public synchronized void subscribeToHotel(HotelSubscription subscription, TravellerInterface travellerInterface) throws RemoteException {
        database.getHotelSubscriptions().add(subscription);
    }
    
    public Database getDatabase() {
        return database;
    }
    
    public void removeExpiredInterests() {
        int i = 0;
        for(FlightSubscription subscription : database.getFlightSubscriptions()){
            LocalDate now = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate expirationDate = LocalDate.parse(subscription.getNotificationTimeSpan(), formatter);            
            if(now.isAfter(expirationDate)){
                database.getFlightSubscriptions().remove(subscription);
            }
        }
    }
            
    /*
    public synchronized void publishFlightChange(Flight flight) throws RemoteException {        
        for (FlightSubscription subscriptionRecord : database.getFlightSubscriptions()) {
            if (subscriptionRecord.getOrigin().equals(flight.getOrigin()) &&
                subscriptionRecord.getDestination().equals(flight.getDestination())
                ) {


                subscriptionRecord.getSubscriber().displayFlightNotification(subscriptionRecord, flight);
            }
        }
    }
    
    public synchronized void publishHotelChange(Hotel hotel) throws RemoteException {
        for (HotelSubscription subscriptionRecord : database.getHotelSubscriptions()) {
            if (subscriptionRecord.getCity().equals(hotel.getCity()) &&
                subscriptionRecord.getNumberOfRooms() <= hotel.getAvailableRooms()
                ) {
                subscriptionRecord.getSubscriber().displayHotelNotification(subscriptionRecord, hotel);
            }
        }
    }
    */
   

}
