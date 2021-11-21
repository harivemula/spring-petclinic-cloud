/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.customers.web;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.customers.model.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.micrometer.core.instrument.Counter;
//Hari
import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Maciej Szarlinski
 */
@RestController
@Timed("petclinic.pet")
@RequiredArgsConstructor
@Slf4j
class PetResource {

    private PetRepository petRepository;
    private OwnerRepository ownerRepository;

    private Map<Integer, Counter> metricCounters = null;

    //@Autowired
    private MeterRegistry meterRegistry;

    @Value("${test.add.delay.enabled:false}")
    boolean addDelay;

    @Value("${test.add.delay.seconds:3}")
    int delaySec;

    @Autowired
    public PetResource(MeterRegistry meterRegistry, PetRepository petRepository, OwnerRepository ownerRepository) {
        this.meterRegistry = meterRegistry;
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
        initPetCounters();
    }



    @GetMapping("/petTypes")
    public List<PetType> getPetTypes() {
        return petRepository.findPetTypes();
    }

    @PostMapping("/owners/{ownerId}/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public Pet processCreationForm(
        @RequestBody PetRequest petRequest,
        @PathVariable("ownerId") int ownerId) {

        final Pet pet = new Pet();
        final Optional<Owner> optionalOwner = ownerRepository.findById(ownerId);
        Owner owner = optionalOwner.orElseThrow(() -> new ResourceNotFoundException("Owner "+ownerId+" not found"));
        owner.addPet(pet);
        
        return save(pet, petRequest);
    }

    @PutMapping("/owners/*/pets/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void processUpdateForm(@RequestBody PetRequest petRequest) {
        int petId = petRequest.getId();
        Pet pet = findPetById(petId);
        save(pet, petRequest);
    }

    private Pet save(final Pet pet, final PetRequest petRequest) {

        pet.setName(petRequest.getName());
        pet.setBirthDate(petRequest.getBirthDate());

        petRepository.findPetTypeById(petRequest.getTypeId())
            .ifPresent(pet::setType);

        log.info("Saving pet {}", pet);

        //Hari
        Counter counter = metricCounters.get(petRequest.getTypeId());
        counter.increment();


        return petRepository.save(pet);
    }

    @GetMapping("owners/*/pets/{petId}")
    public PetDetails findPet(@PathVariable("petId") int petId) {
        return new PetDetails(findPetById(petId));
    }


    private Pet findPetById(int petId) {

        if (addDelay) {
            log.info("forced sleep...");
            try {
                Thread.sleep(delaySec*1000);
            } catch (Exception e) {}
            
        }


        Optional<Pet> pet = petRepository.findById(petId);
        if (!pet.isPresent()) {
            throw new ResourceNotFoundException("Pet "+petId+" not found");
        }
        return pet.get();
    }


    private void initPetCounters() {
        List<PetType> petTypes = this.getPetTypes();
        metricCounters = new HashMap<Integer, Counter>();

        for (PetType petType : petTypes) {
            Counter petCounter = Counter.builder("pets.by.type")
                                    .tag("type", petType.getName())
                                    .description("Number of Pets created by type")
                                    .register(meterRegistry);
             metricCounters.put(petType.getId(), petCounter);
        }
    }


}
