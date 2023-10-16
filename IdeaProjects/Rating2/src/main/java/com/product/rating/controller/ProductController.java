package com.product.rating.controller;

import com.product.rating.domain.Client;
import com.product.rating.domain.RatingDomain;
import com.product.rating.model.RatingModel;
import com.product.rating.services.ClientService;
import com.product.rating.services.RatingService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/reviews")
@CrossOrigin(origins = "http://localhost:4200") // Allow requests from your Angular app
public class ProductController {

    private final ClientService clientService;
    private final RatingService ratingService;
    private final MongoTemplate mongoTemplate;
    public static final Logger lowReviewsLogger = LogManager.getLogger("lowReviews");
    private static final Logger formattedReviewLogger = LogManager.getLogger("formattedReview");

    @Autowired
    public ProductController(RatingService ratingService, MongoTemplate mongoTemplate, ClientService clientService) {
        this.ratingService = ratingService;
        this.mongoTemplate = mongoTemplate;
        this.clientService = clientService;
    }

    @Value("${collection.name}")
    private String collectionName; // Inject the collection name from application.properties

    @Value("${customlog.dateformat}")
    private String customLogDateFormat; // Inject the custom log date format

    @Value("${customlog.messageformat}")
    private String customLogMessageFormat; // Inject the custom log message format


    @PostMapping("/createCollection")
    public ResponseEntity<String> createCollection(@RequestParam String collectionName) {
        try {
            ratingService.createCollection(this.collectionName); // Use the injected collection name
            return new ResponseEntity<>("Collection created successfully", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("Error creating collection: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/deleteCollection/{collectionName}")
    public ResponseEntity<String> deleteCollection(@PathVariable String collectionName) {
        try {
            ratingService.deleteCollection(this.collectionName); // Use the injected collection name
            return new ResponseEntity<>("Collection deleted successfully", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Error deleting collection: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //C (R) U D
    @GetMapping("/viewAllReviews")
    public ResponseEntity<List<RatingModel>> viewAllReviews() {
        List<RatingModel> ratings = ratingService.viewAllReviews();
        if (ratings.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(ratings, HttpStatus.OK);
        }
    }

    @GetMapping("/viewReviews/{collectionName}")
    public ResponseEntity<List<RatingModel>> viewReviewsInCollection(@PathVariable String collectionName) {
        List<RatingModel> reviews = ratingService.viewReviewsInCollection(collectionName);
        if (reviews.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(reviews, HttpStatus.OK);
        }
    }

    //C (R) U D
    @GetMapping("/viewLatestReviews")
    public ResponseEntity<List<RatingModel>> viewLatestReviews(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        List<RatingModel> latestReviews = ratingService.getLatestReviews(limit);

        if (latestReviews.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(latestReviews, HttpStatus.OK);
        }
    }

    //C (R) U D
    @GetMapping("/viewByRateCode")
    public ResponseEntity<List<RatingModel>> viewReviewsByRateCode(@RequestParam int rateCode) {
        List<RatingModel> reviews = ratingService.findReviewsByRateCode(rateCode);

        if (reviews.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(reviews, HttpStatus.OK);
        }
    }

    //(C) R U D
    @PostMapping("/insertReview")
    public ResponseEntity<String> insertRating(@RequestHeader("clientId") String clientId, @RequestBody RatingModel rating) {
        try {
            System.out.println("Rating is " + rating.getRateCode());

            if (rating.getRateCode() < 3) {
                lowReviewsLogger.info("Low rating inserted with RateCode: " + rating.getRateCode());
            }

            // Call the logLowRatingReview method here to log low-rated reviews
            ratingService.logLowRatingReview(rating);

            // Use the injected collection name
            return ratingService.insertRating(collectionName, rating, clientId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to insert Rating: " + e.getMessage());
        }
    }


    //C R (U) D
    @PutMapping("/updateReview/{id}")
    public ResponseEntity<RatingDomain> updateReview(
            @PathVariable String id,
            @RequestBody RatingDomain updatedRating
    ) {
        return ratingService.updateReviewById(collectionName, id, updatedRating);
    }

    //C R U (D)
    //spring expression language - @authorizationServiceImpl.verifyToken(#token, #clientId, 'delete')
    @PreAuthorize("@authorizationServiceImpl.verifyToken(#token, #clientId, 'delete')")
    @DeleteMapping("/deleteReview/{id}")
    public ResponseEntity<String> deleteReview(@PathVariable String id) {
        boolean deleted = ratingService.deleteReviewById(collectionName, id);
        if (deleted) {
            return ResponseEntity.ok("Deleted Rating with ID: " + id);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("@authorizationServiceImpl.verifyToken(#token, #clientId, 'delete')")
    @DeleteMapping("/deleteReview")
    public ResponseEntity<String> deleteReview(HttpServletRequest request) {
        String clientId = request.getHeader("clientId");
        boolean deleted = ratingService.deleteReviewByClientId(collectionName, clientId);
        if (deleted) {
            return ResponseEntity.ok("Deleted Rating with Client ID: " + clientId);
        } else {
            return ResponseEntity.notFound().build();
        }
    }



    //create a new client endpoint -- pass in client secret/secret
    @PostMapping("/newClient")
    public String newClient(@RequestBody String clientSecret){
        return clientService.createNewClient(clientSecret);
    }

}