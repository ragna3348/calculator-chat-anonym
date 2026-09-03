package com.example.utils

import java.util.Random

enum class MathOperationType(val symbol: String, val title: String, val description: String) {
    ADDITION("+", "Penjumlahan", "Formula penjumlahan (contoh: 1+4, 2+8, 10+11)"),
    SUBTRACTION("-", "Pengurangan", "Formula pengurangan (contoh: 9-4, 15-7, 20-8)"),
    MULTIPLICATION("×", "Perkalian", "Formula perkalian (contoh: 2×3, 4×5, 7×8)"),
    DIVISION("÷", "Pembagian", "Formula pembagian (contoh: 12÷3, 20÷4, 30÷5)")
}

data class MathUsernameCandidate(
    val formula: String,
    val operand1: Int,
    val operatorSymbol: String,
    val operand2: Int,
    val calculationResult: Int,
    val readableDescription: String
)

object MathUsernameGenerator {

    private val random = Random()

    /**
     * Normalizes a formula string so operators like *, x, X become ×, and / becomes ÷, with spaces stripped.
     */
    fun normalizeFormula(raw: String): String {
        return raw.trim()
            .replace(" ", "")
            .replace("*", "×")
            .replace("x", "×")
            .replace("X", "×")
            .replace("/", "÷")
            .replace(":", "÷")
    }

    /**
     * Generates a specified number of unique, non-colliding math usernames based on the selected operation.
     */
    fun generateUniqueOptions(
        operationType: MathOperationType,
        existingUsernames: Set<String>,
        count: Int = 3
    ): List<MathUsernameCandidate> {
        val normalizedExisting = existingUsernames.map { normalizeFormula(it) }.toSet()
        val candidates = mutableListOf<MathUsernameCandidate>()
        val seenInBatch = mutableSetOf<String>()

        var attempts = 0
        val maxAttempts = 200

        while (candidates.size < count && attempts < maxAttempts) {
            attempts++
            val candidate = generateCandidate(operationType)
            val normalized = normalizeFormula(candidate.formula)

            if (!normalizedExisting.contains(normalized) &&
                !seenInBatch.contains(normalized) &&
                normalized != "99+99" // Reserved for master vault unlock
            ) {
                seenInBatch.add(normalized)
                candidates.add(candidate)
            }
        }

        // Fallback generator in case of high density
        var fallbackSeed = 1
        while (candidates.size < count) {
            val candidate = generateFallbackCandidate(operationType, fallbackSeed++)
            val normalized = normalizeFormula(candidate.formula)
            if (!normalizedExisting.contains(normalized) && !seenInBatch.contains(normalized)) {
                seenInBatch.add(normalized)
                candidates.add(candidate)
            }
        }

        return candidates
    }

    private fun generateCandidate(operationType: MathOperationType): MathUsernameCandidate {
        return when (operationType) {
            MathOperationType.ADDITION -> {
                val a = random.nextInt(25) + 1 // 1..25
                val b = random.nextInt(35) + 1 // 1..35
                val formula = "$a+$b"
                MathUsernameCandidate(
                    formula = formula,
                    operand1 = a,
                    operatorSymbol = "+",
                    operand2 = b,
                    calculationResult = a + b,
                    readableDescription = "$a ditambah $b sama dengan ${a + b}"
                )
            }
            MathOperationType.SUBTRACTION -> {
                val b = random.nextInt(20) + 1 // 1..20
                val diff = random.nextInt(25) + 1 // 1..25
                val a = b + diff // Ensures a > b
                val formula = "$a-$b"
                MathUsernameCandidate(
                    formula = formula,
                    operand1 = a,
                    operatorSymbol = "-",
                    operand2 = b,
                    calculationResult = diff,
                    readableDescription = "$a dikurangi $b sama dengan $diff"
                )
            }
            MathOperationType.MULTIPLICATION -> {
                val a = random.nextInt(12) + 2 // 2..13
                val b = random.nextInt(12) + 2 // 2..13
                val formula = "$a×$b"
                MathUsernameCandidate(
                    formula = formula,
                    operand1 = a,
                    operatorSymbol = "×",
                    operand2 = b,
                    calculationResult = a * b,
                    readableDescription = "$a dikali $b sama dengan ${a * b}"
                )
            }
            MathOperationType.DIVISION -> {
                val divisor = random.nextInt(10) + 2 // 2..11
                val quotient = random.nextInt(12) + 2 // 2..13
                val dividend = divisor * quotient
                val formula = "$dividend÷$divisor"
                MathUsernameCandidate(
                    formula = formula,
                    operand1 = dividend,
                    operatorSymbol = "÷",
                    operand2 = divisor,
                    calculationResult = quotient,
                    readableDescription = "$dividend dibagi $divisor sama dengan $quotient"
                )
            }
        }
    }

    private fun generateFallbackCandidate(operationType: MathOperationType, seed: Int): MathUsernameCandidate {
        return when (operationType) {
            MathOperationType.ADDITION -> {
                val a = 50 + seed
                val b = 70 + (seed * 2)
                MathUsernameCandidate("$a+$b", a, "+", b, a + b, "$a ditambah $b = ${a + b}")
            }
            MathOperationType.SUBTRACTION -> {
                val a = 100 + (seed * 3)
                val b = 25 + seed
                MathUsernameCandidate("$a-$b", a, "-", b, a - b, "$a dikurangi $b = ${a - b}")
            }
            MathOperationType.MULTIPLICATION -> {
                val a = 15 + seed
                val b = 3 + (seed % 5)
                MathUsernameCandidate("$a×$b", a, "×", b, a * b, "$a dikali $b = ${a * b}")
            }
            MathOperationType.DIVISION -> {
                val divisor = 3 + (seed % 4)
                val dividend = divisor * (15 + seed)
                MathUsernameCandidate("$dividend÷$divisor", dividend, "÷", divisor, dividend / divisor, "$dividend dibagi $divisor = ${dividend / divisor}")
            }
        }
    }

    /**
     * Checks if a given input string matches a valid basic binary math expression (e.g., "1+1", "10-5", "3*4", "12/3").
     */
    fun isMathExpression(input: String): Boolean {
        val normalized = normalizeFormula(input)
        val pattern = Regex("^\\d+([+\\-×÷])\\d+$")
        return pattern.matches(normalized)
    }
}
