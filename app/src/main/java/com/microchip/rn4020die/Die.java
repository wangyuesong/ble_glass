/*
 * Copyright (C) 2014 Microchip Technology Inc. and its subsidiaries.  You may use this software and any derivatives
 * exclusively with Microchip products.
 *
 * THIS SOFTWARE IS SUPPLIED BY MICROCHIP "AS IS".  NO WARRANTIES, WHETHER EXPRESS, IMPLIED OR STATUTORY, APPLY TO THIS
 * SOFTWARE, INCLUDING ANY IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR
 * PURPOSE, OR ITS INTERACTION WITH MICROCHIP PRODUCTS, COMBINATION WITH ANY OTHER PRODUCTS, OR USE IN ANY APPLICATION. 
 *
 * IN NO EVENT WILL MICROCHIP BE LIABLE FOR ANY INDIRECT, SPECIAL, PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSS, DAMAGE,
 * COST OR EXPENSE OF ANY KIND WHATSOEVER RELATED TO THE SOFTWARE, HOWEVER CAUSED, EVEN IF MICROCHIP HAS BEEN ADVISED OF
 * THE POSSIBILITY OR THE DAMAGES ARE FORESEEABLE.  TO THE FULLEST EXTENT ALLOWED BY LAW, MICROCHIP'S TOTAL LIABILITY ON
 * ALL CLAIMS IN ANY WAY RELATED TO THIS SOFTWARE WILL NOT EXCEED THE AMOUNT OF FEES, IF ANY, THAT YOU HAVE PAID
 * DIRECTLY TO MICROCHIP FOR THIS SOFTWARE.
 *
 * MICROCHIP PROVIDES THIS SOFTWARE CONDITIONALLY UPON YOUR ACCEPTANCE OF THESE TERMS.
 *
 *
 * File:        Die.java
 * Date:        July 24, 2014
 * Compiler:    JDK and Android SDK
 *
 * Class to represent a die that can roll a number from 1 to 6
 *
 */

package com.microchip.rn4020die;

public class Die {

    private byte spots;							//Variable to represent the spots showing on the die
    private static byte dieCount = 0;			//Variable to track the number of dice created

//--------------------------------------------------------------------------
//Constructor creates a Die and initializes the value of spots
    public Die() {
        spots = (byte) (1 + (Math.random() * 6));
        dieCount++;
    }

//--------------------------------------------------------------------------
//Method rolls the die and returns the new value of spots
    public byte Roll() {
        byte oldSpots = spots;
        while (spots == oldSpots)				//Ensure each roll is different for demo - remove for a real die!
        	spots = (byte) (1 + (Math.random() * 6));
        return spots;
    }

//--------------------------------------------------------------------------
//Method returns the value of spots without rolling the die
    public byte View() {
        return spots;
    }

//--------------------------------------------------------------------------
//Method returns the total number of die objects created
    public byte NumDie() {
        return dieCount;
    }
}
