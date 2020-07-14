package main

import (
	"bytes"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"golang.org/x/net/websocket"
	"io/ioutil"
	"log"
	"math/big"
	"net/http"
	"strconv"
	"time"
)

type ReactiveStockSimulator struct {
	serviceGatewayUrl string
	endpoints         map[string]map[string]Endpoint
	traders           []Trader
}

type Endpoint struct {
	method string
	url    string
}

type Trader struct {
	username string
	jwtToken string
}

func main() {
	log.Println("Starting Interpolator")

	reactiveStock := ReactiveStockSimulator{
		serviceGatewayUrl: "http://localhost:9000",
		endpoints: map[string]map[string]Endpoint{
			"TraderService": {
				"login": Endpoint{http.MethodPost, "/api/trader/login"},
				"register": Endpoint{http.MethodPost, "/api/trader/register"},
				"balance": Endpoint{http.MethodGet, "/api/trader/balance"},
				"asset": Endpoint{http.MethodGet, "/api/trader/asset/:asset"},
				"assets": Endpoint{http.MethodGet, "/api/trader/assets"},
				"putAsset": Endpoint{http.MethodPut, "/api/trader/asset/:asset"},
			},
			"AssetService": {
				"asset": Endpoint{http.MethodGet, "/api/asset/:asset"},
				"assets": Endpoint{http.MethodGet, "/api/asset/assets"},
			},
			"TransactionService": {
				"order": Endpoint{http.MethodPost, "/api/transaction/order"},
			},
			"TableService": {
				"resolvedTransactionsStream":
					Endpoint{http.MethodGet, "/resolvedTransactionsStream"},
			},
		},
	}

	// register such many traders and interpolate transactions over them
	const NumOfTraders = 5

	log.Println("Registering Traders")
	for i := 1; i <= NumOfTraders; i++ {
		go reactiveStock.register(i)
	}

	// TODO: solve better way
	// easy approach for awaiting Eventual Consistency, 5 secs per trader
	time.Sleep(3 * NumOfTraders * time.Second)

	log.Println("Logging traders in")
	for i := 1; i <= NumOfTraders; i++ {
		reactiveStock.login(i)
	}

	log.Println("Subscribing to resolved transactions stream")
	go reactiveStock.resolvedTransactions()

	log.Println("Placing orders")
	for i := 0; i < 10; i++ {
		go reactiveStock.placeOrder("BUY")
		go reactiveStock.placeOrder("SELL")
	}

	// Keep Interpolator running
	select {}
}

func (r *ReactiveStockSimulator) register(index int) {
	type RegisterPayload struct {
		Username  string `json:"username"`
		Password  string `json:"password"`
		Email     string `json:"email"`
		FirstName string `json:"firstName"`
		LastName  string `json:"lastName"`
	}

	register := RegisterPayload{
		Username:  "trader" + strconv.Itoa(index),
		Password:  "pass123",
		Email:     "example" + strconv.Itoa(index) + "@localhost",
		FirstName: "John",
		LastName:  "Doe",
	}

	jsonRegister, _ := json.Marshal(register)
	r.exec(r.endpoints["TraderService"]["register"], jsonRegister, nil)
}

func (r *ReactiveStockSimulator) login(index int) {
	type LoginPayload struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}

	login := LoginPayload{
		Username:  "trader" + strconv.Itoa(index),
		Password:  "pass123",
	}

	jsonLogin, _ := json.Marshal(login)
	response := r.exec(r.endpoints["TraderService"]["login"], jsonLogin, nil)

	var loginResponse struct {
		AuthToken string `json:"authToken"`
	}

	err := json.Unmarshal(response, &loginResponse)
	if err != nil {
		panic(err)
	}

	r.traders = append(
		r.traders, Trader{username: login.Username, jwtToken: loginResponse.AuthToken})
}

func (r *ReactiveStockSimulator) placeOrder(orderType string) {
	type PlaceOrderPayload struct {
		Asset     string             `json:"asset"`
		// TODO: change to Decimal
		Price     float64            `json:"price"`
		Quantity  float64            `json:"quantity"`
		OrderType map[string]string  `json:"orderType"`
	}

	// TODO: interpolate on BTC transactions
	placeOrder := PlaceOrderPayload{
		Asset:  "BTC",
		Price:  1178.20,
		Quantity: 10,
		OrderType: map[string]string{"type": orderType},
	}

	placeOrderLogin, _ := json.Marshal(placeOrder)

	// distribute traders
	nBig, err := rand.Int(rand.Reader, big.NewInt(int64(len(r.traders))))
	if err != nil {
		panic(err)
	}

	randomTraderIndex := nBig.Int64()

	r.exec(
		r.endpoints["TransactionService"]["order"],
		placeOrderLogin, &r.traders[randomTraderIndex])
}

func (r *ReactiveStockSimulator) resolvedTransactions() {
	websocketUrl :=
		"ws://localhost:9000" + r.endpoints["TableService"]["resolvedTransactionsStream"].url

	ws, err := websocket.Dial(websocketUrl, "", r.serviceGatewayUrl)
	if err != nil {
		panic(err)
	}
	defer ws.Close()

	type ResolvedTransaction struct {
		TransactionId string  `json:"transactionId"`
		Asset         string  `json:"asset"`
		Price         float64 `json:"price"`
		Quantity      float64 `json:"quantity"`
		Timestamp     string  `json:"timestamp"`
		Buyer         string  `json:"buyer"`
		Seller        string  `json:"seller"`
	}

	for {
		var resolvedTransaction ResolvedTransaction
		if err = websocket.JSON.Receive(ws, &resolvedTransaction); err != nil {
			panic(err)
		}

		log.Println("Resolved transaction:", resolvedTransaction)
	}
}

func (r *ReactiveStockSimulator) exec(endpoint Endpoint, jsonValue []byte, trader *Trader) []byte {
	url := r.serviceGatewayUrl + endpoint.url

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewBuffer(jsonValue))
	if err != nil {
		panic(err)
	}

	if trader != nil {
		req.Header.Add("Authorization", fmt.Sprintf("Bearer %s", trader.jwtToken))
	}

	client := http.DefaultClient
	resp, err := client.Do(req)
	if err != nil {
		panic(err)
	}

	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		panic(err)
	}

	// TODO: handle debug printing?
	//fmt.Printf("%v", string(body))

	return body
}
